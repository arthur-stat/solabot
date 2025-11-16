package com.arth.solabot.plugin.custom.pjsk.func;

import com.arth.solabot.core.infrastructure.LocalData;
import com.arth.solabot.core.infrastructure.exception.InternalServerErrorException;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import com.arth.solabot.core.infrastructure.network.service.ImageRequestService;
import com.arth.solabot.plugin.custom.pjsk.model.PjskCard;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class AssetsBundleResources {

    private final LocalData localData;
    private final ImageRequestService imageNetworkService;

    @Value("${app.parameter.plugin.pjsk.external-api.uni.thumbnail-api}")
    private String THUMBNAIL_API;

    @Value("${app.parameter.plugin.pjsk.cache_cards_thumbnails}")
    private boolean ENABLE_THUMBNAIL_CACHE;

    /**
     * 用于缓存或获取卡缩略图
     *
     * @param card
     * @return
     */
    public BufferedImage getOrCacheThumbnailByCard(PjskCard card) {
        try {
            if (ENABLE_THUMBNAIL_CACHE) {
                Files.createDirectories(LocalData.RENDER_THUMBNAILS_BASE);
                Path path = localData.getCardThumbnailImgPath(card.getAssetsbundleName(), card.getSpecialTrainingStatus());
                if (path.toFile().exists()) {
                    return ImageIO.read(path.toFile());
                } else {
                    BufferedImage bufferedImage = getThumbnailOnline(card.getAssetsbundleName(), card.getSpecialTrainingStatus());
                    ImageIO.write(bufferedImage, "png", path.toFile());
                    return bufferedImage;
                }
            }
            return getThumbnailOnline(card.getAssetsbundleName(), card.getSpecialTrainingStatus());
        } catch (IOException e) {
            if (e instanceof java.nio.file.NoSuchFileException) {
                throw new ResourceNotFoundException();
            }
            throw new InternalServerErrorException();
        }
    }


    private BufferedImage getThumbnailOnline(String assetsbundleName, String specialTrainingStatus) {
        return imageNetworkService.getBufferedImg(THUMBNAIL_API
                .replace("{assetbundle_name}", assetsbundleName)
                .replace("{status}", specialTrainingStatus));
    }
}
