package com.arth.solabot.adapter.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApiPaths {

    /*
      CacheService 相关 api 路径
    */
    public static final String CACHE_IMG_PNG = "/api/v1/cache/imgs/png/{uuid}";
    public static final String CACHE_IMG_GIF = "/api/v1/cache/imgs/gif/{uuid}";
    public static final String CACHE_IMG = "/api/v1/cache/imgs/{type}/{uuid}";

    public String buildPngUrl(String uuid) {
        return DOMAIN_NAME + "/api/v1/cache/imgs/png/" + uuid;
    }

    public String buildGifUrl(String uuid) {
        return DOMAIN_NAME + "/api/v1/cache/imgs/gif/" + uuid;
    }

    public String buildImgUrl(String type, String uuid) {
        return DOMAIN_NAME + "/api/v1/cache/imgs/" + type + "/" + uuid;
    }


    /*
      插件 Pjsk 相关 api 路径
    */
    public static final String PJSK_MYSEKAI_MAP = "/api/v1/pjsk/resource/{region}/mysekai/{id}/map";
    public static final String PJSK_MYSEKAI_OVERVIEW = "/api/v1/pjsk/resource/{region}/mysekai/{id}/overview";
    public static final String PJSK_SUITE_UPLOAD = "/api/v1/pjsk/upload/suite";
    public static final String PJSK_MYSEKAI_UPLOAD = "/api/v1/pjsk/upload/mysekai";
    public static final String SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN = "/api/v1/pjsk/module/cn/mysekai";
    public static final String MYSEKAI_UPLOAD_PROXY = "/api/v1/pjsk/proxy-upload/mysekai";


    public String buildMysekaiMapUrl(String region, String id) {
        return DOMAIN_NAME + "/api/v1/pjsk/resource/" + region + "/mysekai/" + id + "/map";
    }

    public String buildMysekaiOverviewUrl(String region, String id) {
        return DOMAIN_NAME + "/api/v1/pjsk/resource/" + region + "/mysekai/" + id + "/overview";
    }

    public String getShadowrocketModuleDownloadMysekaiCn() {
        return DOMAIN_NAME + SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN;
    }


    /*
      插件 Gallery 相关 api 路径
    */
    public static final String GALLERY_IMG = "/api/v1/gallery/img/{pid}";
    public static final String GALLERY_THUMBNAILS = "/api/v1/gallery/{role}";

    public String buildGalleryImgUrl(String pid) {
        return DOMAIN_NAME + "/api/v1/gallery/img/" + pid;
    }

    public String buildGalleryThumbnailUrl(String role) {
        return DOMAIN_NAME + "/api/v1/gallery/" + role;
    }


    // **============--  Autowired Filed   --============**
    // **============--  Autowired Filed   --============**
    // **============--  Autowired Filed   --============**


    private ApiPaths(@Value("${app.domain-name}") String domainName) {
        DOMAIN_NAME = domainName;
    }

    public final String DOMAIN_NAME;
}