/*
 * Copyright 2016 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.niord.core.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.niord.core.batch.vo.BatchSetVo;
import org.niord.core.repo.RepositoryService;
import org.niord.core.util.JsonUtils;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerConfig;
import javax.ejb.TimerService;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A <i>batch set</i> is a folder or a zipped archive that contains the following files:
 * <ul>
 *     <li>batch-set.json: Contains an array of BatchSetVo instances, each representing
 *              a batch job</li>
 *     <li>The batch job data files referenced in the batch-set.json file</li>
 * </ul>
 * <p>
 * A batch set can either be uploaded from the Admin -> Batch Jobs page or
 * via a "niord.batch-set" System setting.
 */
@Singleton
@Startup
@SuppressWarnings("unused")
public class BatchSetService {

    @Inject
    Logger log;

    @Resource
    TimerService timerService;

    @Inject
    BatchService batchService;

    @Inject
    RepositoryService repositoryService;


    /**
     * Check if a batch set has been specified via the "niord.batch-set" system setting
     **/
    @PostConstruct
    public void init() {
        if (StringUtils.isNotBlank(System.getProperty("niord.batch-set"))) {

            Path folder = Paths.get(System.getProperty("niord.batch-set"));
            try {
                executeBatchSetFromArchiveOrFolder(folder);
            } catch (Exception e) {
                log.error("Error reading batch set from folder " + folder, e);
            }
        }
    }


    /**
     * Executes the batch set from the given zip archive or folder
     * @param path the archive or folder
     */
    private void executeBatchSetFromArchiveOrFolder(Path path) throws Exception {

        if (Files.isRegularFile(path)) {
            // Zip archive
            try (FileInputStream in = new FileInputStream(path.toFile())) {
                extractAndExecuteBatchSetArchive(in, null);
            }

        } else if (Files.isDirectory(path)) {
            // Exploded folder
            BatchSetSpecification batchSetSpec = readBatchSetFromFolder(path);
            executeBatchSet(batchSetSpec, null);

        } else {
            throw new Exception("Non-existing batch set archive or folder " + path);
        }
    }


    /**
     * Reads the batch set specification from the given folder
     * @param folder the folder
     * @return the batch set specification
     */
    private BatchSetSpecification readBatchSetFromFolder(Path folder) throws Exception {

        if (!Files.isDirectory(folder)) {
            throw new Exception("Non-existing batch set folder " + folder);
        }

        // Read the batch-set.json file
        Path batchSetFile = folder.resolve("batch-set.json");
        if (!Files.isRegularFile(batchSetFile)) {
            throw new Exception("Batch set did not contain a batch-set.json file");
        }

        List<BatchSetVo> batchSetItems = JsonUtils.readJson(
                new TypeReference<List<BatchSetVo>>() {},
                batchSetFile);

        return new BatchSetSpecification(folder, batchSetItems);
    }


    /** Called to start a batch set execution from a zipped batch set archive **/
    @SuppressWarnings("all")
    public void extractAndExecuteBatchSetArchive(InputStream inputStream, StringBuilder txt) throws Exception {

        // Create a temporary destination folder
        String tempArchiveRepoPath = repositoryService.getNewTempDir().getPath();
        Path folder = repositoryService.getRepoRoot().resolve(tempArchiveRepoPath);
        Files.createDirectories(folder);

        // Unzip the archive
        log.info("Unzipping batch set zip archive to folder " + folder);
        //get the zip file content
        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry = zis.getNextEntry();
        while (entry != null) {
            File entryDestination = new File(folder.toFile(), entry.getName());
            if (entry.isDirectory()) {
                entryDestination.mkdirs();
            } else {
                entryDestination.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(entryDestination)) {
                    IOUtils.copy(zis, out);
                }
            }
            entry = zis.getNextEntry();
        }
        zis.closeEntry();

        // Execute the batch set
        BatchSetSpecification batchSetSpec = readBatchSetFromFolder(folder);
        executeBatchSet(batchSetSpec, txt);
    }


    /**
     * Starts executing a batch set by scheduling each batch job of the specification
     * @param batchSetSpec the batch set specification
     */
    private void executeBatchSet(BatchSetSpecification batchSetSpec, StringBuilder txt) {
        txt = txt != null ? txt : new StringBuilder();

        for (BatchSetVo batchSetItem : batchSetSpec.getBatchSetItems()) {

            BatchSetExecution execution = new BatchSetExecution(batchSetSpec, batchSetItem);
            long delay = batchSetItem.getDelay();

            txt.append("Scheduling batch job ")
                    .append(batchSetItem.getJobName())
                    .append(" in ")
                    .append(delay)
                    .append(" ms\n");

            timerService.createSingleActionTimer(delay, new TimerConfig(execution, false));
        }
    }


    /**
     * Called in order to execute a batch set item
     */
    @Timeout
    private void executeBatchSetItem(Timer timer) {
        BatchSetExecution batchSetExecution = (BatchSetExecution)timer.getInfo();

        String batchJobName = batchSetExecution.getBatchSetItem().getJobName();
        String batchFileName = batchSetExecution.getBatchSetItem().getFileName();
        Path file = batchSetExecution.getBatchSetSpec().getFolder().resolve(batchFileName);
        if (!Files.isRegularFile(file)) {
            log.error("Batch file for batch set item " + batchJobName + " did not exist: " + file);
            return;
        }
        Map<String, Object> properties = batchSetExecution.getBatchSetItem().getProperties() != null
                ? batchSetExecution.getBatchSetItem().getProperties()
                : new HashMap<>();

        try (InputStream in = new FileInputStream(file.toFile())) {
            batchService.startBatchJobWithDataFile(
                    batchJobName,
                    in,
                    batchFileName,
                    properties);
        } catch (Exception e) {
            log.error("Error executing batch set item " + batchJobName + " from file " + file);
        }
    }


    /** Defines the batch set to execute **/
    public static class BatchSetSpecification implements Serializable {
        Path folder;
        List<BatchSetVo> batchSetItems = new ArrayList<>();

        public BatchSetSpecification() {
        }

        public BatchSetSpecification(Path folder, List<BatchSetVo> batchSetItems) {
            this.folder = folder;
            this.batchSetItems = batchSetItems;
        }

        public Path getFolder() {
            return folder;
        }

        public void setFolder(Path folder) {
            this.folder = folder;
        }

        public List<BatchSetVo> getBatchSetItems() {
            return batchSetItems;
        }

        public void setBatchSetItems(List<BatchSetVo> batchSetItems) {
            this.batchSetItems = batchSetItems;
        }
    }


    /** Used for collecting information needed to execute a batch set item **/
    public static class BatchSetExecution implements Serializable {
        BatchSetSpecification batchSetSpec;
        BatchSetVo batchSetItem;

        public BatchSetExecution() {
        }

        public BatchSetExecution(BatchSetSpecification batchSetSpec, BatchSetVo batchSetItem) {
            this.batchSetSpec = batchSetSpec;
            this.batchSetItem = batchSetItem;
        }

        public BatchSetSpecification getBatchSetSpec() {
            return batchSetSpec;
        }

        public void setBatchSetSpec(BatchSetSpecification batchSetSpec) {
            this.batchSetSpec = batchSetSpec;
        }

        public BatchSetVo getBatchSetItem() {
            return batchSetItem;
        }

        public void setBatchSetItem(BatchSetVo batchSetItem) {
            this.batchSetItem = batchSetItem;
        }
    }

}
