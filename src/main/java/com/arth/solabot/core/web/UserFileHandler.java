package com.arth.solabot.core.web;

import com.arth.solabot.adapter.controller.http.dto.ApiResponse;
import com.arth.solabot.plugin.resource.LocalData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
@Component
@Slf4j
public class UserFileHandler {

    private final LocalData localData;


    public ApiResponse<String> handleUploadedSuite(byte[] file, String region)   {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode node;
        try {
            node = mapper.readTree(file);
        }catch (Exception e) {
            //log.error(e.getMessage(),e);
            try {
                node = PlayerDataDecryptor.forRegion(mapper,PlayerDataDecryptor.Region.valueOf(region.toUpperCase()))
                        .decrypt(file)
                        .toJsonNode();
            }catch (Exception err) {
                log.error(e.getMessage(),e);
                return ApiResponse.error(500_01,"Error while decrypting file");
            }
        }

        try {
            String playerId = node.get("userGamedata").get("userId").asText();
            Path suitePath = localData.getSuitePath(region,playerId);
            switch (region) {
                case "cn" -> Files.createDirectories(LocalData.PJSK_SUITE_CN.toAbsolutePath());
                case "jp" -> Files.createDirectories(LocalData.PJSK_SUITE_JP.toAbsolutePath());
                case "tw" -> Files.createDirectories(LocalData.PJSK_SUITE_TW.toAbsolutePath());
            }

            Files.writeString(suitePath, mapper.writeValueAsString(node));
            return ApiResponse.success("Upload success");

        }catch (IOException e){
            log.error(e.getMessage(),e);
            return ApiResponse.error(500_02,"Error while creating file");
        }
    }

    public ApiResponse<String> handleUploadedMySekaiFile(byte[] file, String region) {
        return null;
    }


}
