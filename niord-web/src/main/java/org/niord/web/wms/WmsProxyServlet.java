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
package org.niord.web.wms;

import org.niord.core.settings.annotation.Setting;
import org.niord.core.util.WebUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Proxy WMS data
 *
 * This servlet will mask out a couple of colours that makes the current Danish WMS service unusable...
 *
 * Define the settings for the "wmsLogin" and "wmsPassword" in the "${niord.home}/niord.json" settings file.
 */
@WebServlet(value = "/wms/*")
public class WmsProxyServlet extends HttpServlet {

    // The color we want transparent
    final static Color[]    MASKED_COLORS   = { Color.WHITE, new Color(221, 241, 239) };
    final static int        COLOR_DIST      = 20;
    final static int        CACHE_TIMEOUT   =  24 * 60 * 60; // 24 hours
    static final String     BLANK_IMAGE     = "/img/blank.png";

    @Inject
    Logger log;

    @Inject
    @Setting(value="wmsProvider", description="The WMS provider")
    String wmsProvider;

    @Inject
    @Setting(value="wmsServiceName", description="The WMS service name")
    String wmsServiceName;

    @Inject
    @Setting(value="wmsLogin", description="The WMS user name")
    String wmsLogin;

    @Inject
    @Setting(value="wmsPassword", description="The WMS password")
    String wmsPassword;

    /**
     * Main GET method
     * @param request servlet request
     * @param response servlet response
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        // Cache for a day
        WebUtils.cache(response, CACHE_TIMEOUT);

        // Check that the WMS provider has been defined using system properties
        if (StringUtils.isBlank(wmsServiceName) || StringUtils.isBlank(wmsProvider) ||
                StringUtils.isBlank(wmsLogin) || StringUtils.isBlank(wmsPassword)) {
            response.sendRedirect(BLANK_IMAGE);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String[]> paramMap = request.getParameterMap();
        String params = paramMap
                .entrySet()
                .stream()
                .map(p -> String.format("%s=%s", p.getKey(), p.getValue()[0]))
                .collect(Collectors.joining("&"));
        params += String.format("&SERVICENAME=%s&LOGIN=%s&PASSWORD=%s", wmsServiceName, wmsLogin, wmsPassword);

        String url = wmsProvider + "?" + params;
        log.trace("Loading image " + url);

        try {
            BufferedImage image = ImageIO.read(new URL(url));
            if (image != null) {
                image = transformWhiteToTransparent(image);

                OutputStream out = response.getOutputStream();
                ImageIO.write(image, "png", out);
                image.flush();
                out.close();
                return;
            }
        } catch (Exception e) {
            log.trace("Failed loading WMS image for URL " + url + ": " + e);
        }

        // Fall back to return a blank image
        response.sendRedirect(BLANK_IMAGE);
    }

    /**
     * Masks out white colour
     * @param image the image to mask out
     * @return the resulting image
     */
    private BufferedImage transformWhiteToTransparent(BufferedImage image) {

        BufferedImage dest = image;
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            dest = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = dest.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();

            image.flush();
        }

        // Mask out the white pixels
        final int width = image.getWidth();
        int[] imgData = new int[width];

        for (int y = 0; y < dest.getHeight(); y++) {
            // fetch a line of data from each image
            dest.getRGB(0, y, width, 1, imgData, 0, 1);
            // apply the mask
            for (int x = 0; x < width; x++) {
                for (Color col : MASKED_COLORS) {
                    int colDist
                            = Math.abs(col.getRed() - (imgData[x] >> 16 & 0x000000FF))
                            + Math.abs(col.getGreen() - (imgData[x] >> 8 & 0x000000FF))
                            + Math.abs(col.getBlue() - (imgData[x] & 0x000000FF));
                    if (colDist <= COLOR_DIST) {
                        imgData[x] = 0x00FFFFFF & imgData[x];
                    }
                }
            }
            // replace the data
            dest.setRGB(0, y, width, 1, imgData, 0, 1);
        }
        return dest;
    }
}
