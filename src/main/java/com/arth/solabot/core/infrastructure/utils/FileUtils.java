package com.arth.solabot.core.infrastructure.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class FileUtils {

    /**
     * 尝试删除文件，如果删除成功则返回 true，删除失败、路径指向目录、文件不存在则返回 false
     *
     * @param path
     * @return
     */
    public boolean tryDeleteFile(Path path) {
        if (path == null) return false;

        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) return false;
            Files.delete(path);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 文件不存在时自动创建，文件已存在时获取该文件
     *
     * @param path
     * @throws IOException
     */
    public static File getOrCreateFile(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
        return path.toFile();
    }
}
