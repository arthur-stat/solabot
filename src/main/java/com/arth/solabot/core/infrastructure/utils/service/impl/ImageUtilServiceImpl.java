package com.arth.solabot.core.infrastructure.utils.service.impl;

import com.arth.solabot.core.infrastructure.utils.service.ImageUtilService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import java.awt.*;
import java.awt.image.BufferedImage;

@Service
@RequiredArgsConstructor
public class ImageUtilServiceImpl implements ImageUtilService {

    @Override
    public int parseIntSafe(IIOMetadataNode n, String attr, int defVal) {
        if (n == null) return defVal;
        try {
            String v = n.getAttribute(attr);
            if (v == null || v.isEmpty()) return defVal;
            return Integer.parseInt(v);
        } catch (Exception e) {
            return defVal;
        }
    }

    @Override
    public BufferedImage deepCopy(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return dst;
    }

    @Override
    public IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        if (root == null) return null;
        if (name.equals(root.getNodeName())) return root;
        for (int i = 0; i < root.getLength(); i++) {
            IIOMetadataNode res = findNode((IIOMetadataNode) root.item(i), name);
            if (res != null) return res;
        }
        return null;
    }

    @Override
    public int readLoopCount(IIOMetadata metadata) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode aes = findNode(root, "ApplicationExtensions");
            if (aes != null) {
                for (int i = 0; i < aes.getLength(); i++) {
                    IIOMetadataNode ae = (IIOMetadataNode) aes.item(i);
                    if ("ApplicationExtension".equals(ae.getNodeName())) {
                        String appID = ae.getAttribute("applicationID");
                        String auth = ae.getAttribute("authenticationCode");
                        if ("NETSCAPE".equals(appID) && "2.0".equals(auth)) {
                            byte[] bytes = (byte[]) ae.getUserObject();
                            if (bytes != null && bytes.length >= 3) {
                                return ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    @Override
    public int readDelayCs(IIOMetadata metadata) {
        try {
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                String delay = gce.getAttribute("delayTime");
                return Integer.parseInt(delay);
            }
        } catch (Exception ignore) {
        }
        return 10;
    }

    @Override
    public void clearRectTransparent(BufferedImage img, int x, int y, int w, int h) {
        Graphics2D g2 = img.createGraphics();
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(x, y, w, h);
        g2.dispose();
    }
}
