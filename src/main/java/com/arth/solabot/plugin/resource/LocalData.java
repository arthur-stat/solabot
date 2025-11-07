package com.arth.solabot.plugin.resource;

import com.arth.solabot.plugin.custom.Gallery;
import com.arth.solabot.plugin.custom.pjsk.objects.PjskCard;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class LocalData {

    /* 服务器本地 Suite 路径 */
    public static final Path PJSK_SUITE_CN = Path.of("archive/pjsk/master_data/suite/cn");
    public static final Path PJSK_SUITE_JP = Path.of("archive/pjsk/master_data/suite/jp");
    public static final Path PJSK_SUITE_TW = Path.of("archive/pjsk/master_data/suite/tw");
    public static final Path PJSK_SUITE_JSON_BASE = Path.of("archive/pjsk/master_data/suite/");

    /* Mysekai 透视图 map 路径 */
    public static final Path PJSK_MYSEKAI_MAP = Path.of("data/pjsk_user_data/mysekai/draw/map");
    /* Mysekai 透视图 overview 路径 */
    public static final Path PJSK_MYSEKAI_OVERVIEW = Path.of("data/pjsk_user_data/mysekai/draw/overview");

    /* 日服 master data 数据路径 */
    public static final Path PJSK_MASTER_DATA_PATH = Path.of("archive/pjsk/master_data");
    public static final Path PJSK_CARDS = Path.of("archive/pjsk/master_data/cards");

    /* PJSK Shadowracket 国服 Mysekai 数据抓包转发模块配置文件路径 */
    public static final Path SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN = Path.of("archive/pjsk/proxy_software_module/shadowrocket/cn_mysekai.txt");

    /* PJSK 卡组渲染器相关资源路径 */
    public static final Path RENDER_BG = Path.of("src/main/resources/static/pjsk/box/background.png");
    public static final Path RENDER_BOARDER_BASE = Path.of("src/main/resources/static/pjsk/box/boarder/");
    public static final Path RENDER_RARITY_BIRTH = Path.of("src/main/resources/static/pjsk/box/rarity_birthday.png");
    public static final Path RENDER_RARITY_STAR = Path.of("src/main/resources/static/pjsk/box/star.png");
    public static final Path RENDER_ATTR_BASE = Path.of("src/main/resources/static/pjsk/box/attribute/");
    public static final Path RENDER_THUMBNAILS_BASE = Path.of("archive/pjsk/master_data/cards/thumbnails/");

    /* Gallery 画廊服务（看看你的插件）资源路径 */
    public static final Path GALLERY_DIR_NAME = Path.of("LunabotGallery");
    public static final Path GALLERY_IMG_BASE = Path.of("data/");
    public static final Path GALLERY_IMG_DIR = Path.of("data/LunabotGallery");
    public static final Path GALLERY_THUMBNAILS_BASE = Path.of("data/LunabotGalleryThumbnail/");


    // **===============  本地路径生成与校验方法  ===============**
    // **===============  本地路径生成与校验方法  ===============**
    // **===============  本地路径生成与校验方法  ===============**


    /* Mysekai 透视图本地路径方法 */
    public Resource resolveMysekaiResourcePath(Path baseDir, String region, String id) throws IOException {
        // 400: 参数格式
        if (!region.matches("[a-zA-Z]+") || !id.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid region or id format");
        }

        Path base = baseDir.normalize();
        Path file = base.resolve(region + "_" + id + ".png").normalize();
        verify(file, base);

        return new PathResource(file);
    }

    public Path getGalleryImgPath(String pid) throws IOException {
        Gallery.FilePair filePair = Gallery.getFilePairByPidWithLock(pid);
        return GALLERY_IMG_DIR.resolve(filePair.roleName()).resolve(filePair.fileName());
    }

    public Resource getGalleryImgResource(String pid) throws IOException {
        Path file = getGalleryImgPath(pid);
        verify(file, GALLERY_IMG_BASE);
        return new PathResource(file);
    }

    public Path getGalleryRolePath(String role) throws IOException {
        return GALLERY_IMG_DIR.resolve(role);
    }

    public Resource getGalleryThumbnailResource(String role) throws IOException {
        Path file = GALLERY_THUMBNAILS_BASE.resolve(String.format("%s.png", role.toLowerCase()));
        verify(file, GALLERY_THUMBNAILS_BASE);
        return Gallery.getThumbnailWithLock(file);
    }

    public Path getSuitePath(String region, String id) throws IOException {
        Path file = PJSK_SUITE_JSON_BASE.resolve(region).resolve(String.format("%s.json", id));
        verify(file, PJSK_SUITE_JSON_BASE);
        return file;
    }

    public Path getCardBorderImgPath(PjskCard card) throws IOException {
        Path file = RENDER_BOARDER_BASE.resolve(String.format("%s.png", card.getRarities().name().toLowerCase()));
        verify(file, RENDER_BOARDER_BASE);
        return file;
    }

    public Path getCardAttrImgPath(PjskCard card) throws IOException {
        Path file = RENDER_ATTR_BASE.resolve(String.format("%s.png", card.getAttributes().name().toLowerCase()));
        verify(file, RENDER_ATTR_BASE);
        return file;
    }

    public Path getCardThumbnailImgPath(String assetsbundleName, String specialTrainingStatus) throws IOException {
        Path file = RENDER_THUMBNAILS_BASE.resolve(String.format("%s_%s.png", assetsbundleName, specialTrainingStatus));
        verify(file, RENDER_THUMBNAILS_BASE);
        return file;
    }


    // **===============  helper  ===============**
    // **===============  helper  ===============**
    // **===============  helper  ===============**


    public void verify(Path path, Path base) throws IOException {
        // 403: 安全校验，防止目录穿越
        if (!path.startsWith(base)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        // 404
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new FileNotFoundException("File not found");
        }
    }
}
