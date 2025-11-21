package com.arth.solabot.adapter.controller.http;

import com.arth.solabot.adapter.controller.ApiPaths;
import com.arth.solabot.adapter.controller.http.advice.UnwrapData;
import com.arth.solabot.adapter.controller.http.dto.ResponseDTO;
import com.arth.solabot.core.infrastructure.exception.ResourceNotFoundException;
import com.arth.solabot.core.web.service.pjsk.PjskService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PjskController {

    private final PjskService pjskService;

    /**
     * Mysekai 透视 map 请求
     *
     * @param region
     * @param id
     * @return
     * @throws IOException
     */
    @UnwrapData
    @GetMapping(ApiPaths.PJSK_MYSEKAI_MAP)
    public ResponseEntity<ResponseDTO<Resource>> getMysekaiMap(@PathVariable String region, @PathVariable String id) throws IOException {
        Resource resource = pjskService.getMysekaiMap(region, id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ResponseDTO.success(resource));
    }


    /**
     * Mysekai 透视 overview 请求
     *
     * @param region
     * @param id
     * @return
     * @throws IOException
     */
    @UnwrapData
    @GetMapping(ApiPaths.PJSK_MYSEKAI_OVERVIEW)
    public ResponseEntity<ResponseDTO<Resource>> getMysekaiOverview(@PathVariable String region, @PathVariable String id) throws IOException {
        Resource resource = pjskService.getMysekaiOverview(region, id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(ResponseDTO.success(resource));
    }


    /**
     * Shadowrocket 模块下载请求：国服 Mysekai
     *
     * @return
     * @throws IOException
     */
    @UnwrapData
    @GetMapping(ApiPaths.SHADOWROCKET_MODULE_DOWNLOAD_MYSEKAI_CN)
    public ResponseEntity<ResponseDTO<Resource>> getShadowrocketModuleForCnMysekai() {
        try {
            Resource resource = pjskService.getShadowrocketModuleForCnMysekai();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/plain; charset=utf-8"));
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"");
            return ResponseDTO.okEntity(resource, headers);

        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            return ResponseDTO.internalErrorEntity("server error");
        }
    }


    /**
     * 模块重定向至此处，后端负责向游戏服务器请求数据；
     * 作为要求进行透明代理请求的特殊方法，该方法单独特殊处理，不包装为 ResponseDTO
     *
     * @param request
     * @param original
     * @return
     * @throws IOException
     */
    @RequestMapping(path = ApiPaths.MYSEKAI_UPLOAD_PROXY, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Resource> proxyUpload(
            HttpServletRequest request,
            @RequestParam("original") String original
    ) throws IOException {
        return pjskService.proxyUpload(request, original);
    }


    /**
     * Suite 文件的 Web 上传接口
     *
     * @param file
     * @param region
     * @return
     * @throws IOException
     */
    @PostMapping(ApiPaths.PJSK_SUITE_UPLOAD)
    public ResponseEntity<ResponseDTO<String>> uploadSuite(
            @RequestParam("file") MultipartFile file,
            @RequestParam("region") String region
    ) throws IOException {
        pjskService.handleSuiteUpload(file, region);
        return ResponseDTO.okEntity();
    }

    /**
     * Mysekai 文件的 Web 上传接口
     *
     * @param file
     * @param region
     * @param gameId 游戏ID
     * @return
     * @throws IOException
     */
    @PostMapping(ApiPaths.PJSK_MYSEKAI_UPLOAD)
    public ResponseEntity<ResponseDTO<String>> uploadMysekai(
            @RequestParam("file") MultipartFile file,
            @RequestParam("region") String region,
            @RequestParam("gameId") String gameId
    ) throws IOException {
        pjskService.handleMysekaiUpload(file, region, gameId);
        return ResponseDTO.okEntity();
    }
}