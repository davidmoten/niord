/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.niord.web.aton;

import org.niord.core.aton.Aton;
import org.niord.core.aton.AtonSearchParams;
import org.niord.core.aton.AtonService;
import org.niord.core.repo.RepositoryService;
import org.niord.core.util.GlobalMercator;
import org.niord.core.util.GraphicsUtils;
import org.apache.commons.io.IOUtils;
import org.niord.model.PagedSearchResultVo;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * Feeds AtoN data as bitmaps.
 * Can be used for servicing an OpenStreetMap Layer in Openlayers.
 * The layer should be configured to have the url "/rest/aton-tiles/${z}/${x}/${y}.png"
 * <p>
 * The handling of blank tiles in particular is un-optimal. This is due to problems getting the service
 * to work with Microsoft IE and Edge:
 * <ul>
 *     <li>IE and Edge will abort redirection requests, preventing us from redirecting blank tiles to
 *         a common global bitmap, which would have been more efficient.</li>
 *     <li>This also prevents us from redirecting the streaming of tiles to the repository service.</li>
 *     <li>The tiles have to be 256x256, preventing us from streaming a 1x1 blank tile.</li>
 * </ul>
 */
@javax.ws.rs.Path("/aton-tiles")
public class AtonTileRestService {

    static final int        TILE_SIZE           = 256;
    static final int        TILE_TTL_HOURS      = 24; // A tile file is refreshed every 24 hours...
    static final String     TILE_REPO_FOLDER    = "aton_tiles";
    static final Color      ATON_COLOR          = new Color(200, 0, 0);

    @Inject
    Logger log;

    @Inject
    AtonService atonService;

    @Inject
    AtonBlankTileCache blankTileCache;

    @Inject
    RepositoryService repositoryService;

    /**
     * Streams the given tile
     */
    @GET
    @javax.ws.rs.Path("/{z}/{x}/{y}.png")
    public Response streamTile(@PathParam("z") int z, @PathParam("x") int x, @PathParam("y") int y,
                                  @Context Request request) throws IOException {

        try {
            // Next, check if the tile exists in the repository
            Path file = repositoryService.getRepoRoot()
                    .resolve(TILE_REPO_FOLDER)
                    .resolve(String.valueOf(z))
                    .resolve(String.valueOf(x))
                    .resolve(String.valueOf(y) + ".png");

            long ttlMs = 1000L * 60L * 60L * TILE_TTL_HOURS;
            Date expirationDate = new Date(System.currentTimeMillis() + ttlMs);

            // Check if the tile is a known blank tile
            if (blankTileCache.getCache().containsKey(file.toString())) {
                return streamBlankTile(expirationDate);

            } else if (Files.exists(file) &&
                    System.currentTimeMillis() < Files.getLastModifiedTime(file).toMillis() + ttlMs) {
                // The tile exists and is not expired

                // Check for an ETag match
                EntityTag etag = entityTagForFile(file);
                Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);
                if (responseBuilder != null) {
                    // Etag match
                    log.trace("File unchanged. Return code 304");
                    return responseBuilder
                            .expires(expirationDate)
                            .build();
                } else {
                    log.trace("Return existing tile " + file);
                    return streamTile(file, expirationDate, etag);
                }
            }


            // Search all messages in the bounds of the tile
            long t0 = System.currentTimeMillis();
            GlobalMercator mercator = new GlobalMercator();
            double[] bounds = mercator.TileLatLonBounds(x, y, z);

            // Convert to mapExtents search parameters
            AtonSearchParams param = new AtonSearchParams()
                .mapExtents(-bounds[2], bounds[1], -bounds[0], bounds[3]);

            // Compute the atons of the tile extent
            PagedSearchResultVo<Aton> atons = atonService.search(param);

            // If the search result is empty, return blank and cache the result
            if (atons.getData().isEmpty()) {
                blankTileCache.getCache().put(file.toString(), file.toString());
                return streamBlankTile(expirationDate);
            }

            // Generate an image
            BufferedImage image = generateAtonTile(z, bounds, mercator, atons.getData());

            // Write the image to the repository
            checkCreateParentDirs(file);
            ImageIO.write(image, "png", file.toFile());
            log.debug("Generated " + file + " in " + (System.currentTimeMillis() - t0) + " ms");

            return streamTile(file, expirationDate, null);

        } catch (Exception e) {
            log.error(String.format("Error generating tile z=%d, x=%d, y=%d. Error=%s", z, x, y, e));
            throw new WebApplicationException(500);
        }
    }


    /**
     * Generates an AtoN tile
     * @param z the zoom level
     * @param bounds the tile bounds
     * @param mercator the mercator calculator
     * @param atons the atons
     * @return the resulting image
     */
    private BufferedImage generateAtonTile(int z, double[] bounds, GlobalMercator mercator, java.util.List<Aton> atons) {

        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        GraphicsUtils.antialias(g2);

        int xy0[] =  mercator.LatLonToPixels(-bounds[0], bounds[1], z);

        atons.stream().forEach(aton -> {

            int xy[] = mercator.LatLonToPixels(aton.getLat(), aton.getLon(), z);
            double px = xy[0] - xy0[0];
            double py = -(xy[1] - xy0[1]);
            double radius = (z < 6) ? 0.5 : 1.0;

            Shape theCircle = new Ellipse2D.Double(px - radius, py - radius, 2.0 * radius, 2.0 * radius);
            g2.setColor(ATON_COLOR);
            g2.fill(theCircle);
        });

        g2.dispose();
        return image;
    }

    /**
     * Ensures that parent directories are created
     * @param file the file whose parent directories will be created
     */
    private void checkCreateParentDirs(Path file) throws IOException {
        if (!Files.exists(file.getParent())) {
            Files.createDirectories(file.getParent());
        }
    }

    /**
     * Streams a tile
     * @param file the tile to stream
     * @param expirationDate the expiration date of the returned tile
     * @param etag the E-Tag. May be null.
     * @return the response
     */
    private Response streamTile(Path file, Date expirationDate, EntityTag etag) throws IOException {
        if (etag == null) {
            etag = entityTagForFile(file);
        }
        return Response
                .ok(file.toFile(), "image/png")
                .expires(expirationDate)
                .tag(etag)
                .build();
    }

    /**
     * Streams a blank tile
     * @param expirationDate the expiration date of the returned tile
     * @return the response
     */
    private Response streamBlankTile(Date expirationDate) throws IOException {
        Path file = repositoryService.getRepoRoot()
                .resolve(TILE_REPO_FOLDER)
                .resolve("blank_256.png");

        // Make sure the blank file is present in the repository
        if (Files.notExists(file)) {
            checkCreateParentDirs(file);
            IOUtils.copy(
                    getClass().getResourceAsStream("/blank_256.png"),
                    new FileOutputStream(file.toFile()));
        }

        log.trace("Streaming blank file: " + file);
        return Response
                .ok(file.toFile(), "image/png")
                .expires(expirationDate)
                .build();
    }

    /**
     * Computes an E-Tag for the file
     * @param file the file
     * @return the E-Tag for the file
     */
    private EntityTag entityTagForFile(Path file) throws IOException {
        return new EntityTag("" + Files.getLastModifiedTime(file).toMillis() + "_" + Files.size(file), true);
    }

}

