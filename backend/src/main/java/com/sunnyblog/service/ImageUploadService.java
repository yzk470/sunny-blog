package com.sunnyblog.service;

import com.sunnyblog.common.BusinessException;
import com.sunnyblog.dto.UploadResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class ImageUploadService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final DateTimeFormatter MONTH_DIR = DateTimeFormatter.ofPattern("yyyyMM");

    @Value("${app.upload.image-dir:./data/uploads}")
    private String imageDir;

    @Value("${app.upload.url-prefix:/api/uploads}")
    private String urlPrefix;

    public UploadResult storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("图片不能超过 5MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("图片读取失败，请重试");
        }

        String extension = detectImageExtension(bytes);
        if (extension == null) {
            throw new BusinessException("只支持 JPG、PNG、GIF、WebP 图片");
        }

        String month = LocalDate.now().format(MONTH_DIR);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path root = Path.of(imageDir).toAbsolutePath().normalize();
        Path targetDir = root.resolve(month).normalize();
        if (!targetDir.startsWith(root)) {
            throw new BusinessException("图片保存路径不合法");
        }

        try {
            Files.createDirectories(targetDir);
            Files.write(targetDir.resolve(filename), bytes);
        } catch (IOException ex) {
            log.error("Unable to save uploaded image to {}: {}", targetDir, ex.getMessage());
            throw new BusinessException("图片保存失败，请检查服务器存储空间");
        }

        String prefix = urlPrefix.endsWith("/")
                ? urlPrefix.substring(0, urlPrefix.length() - 1)
                : urlPrefix;
        String url = prefix + "/" + month + "/" + filename;
        log.info("Image uploaded: {} ({} bytes)", url, bytes.length);
        return new UploadResult(url, filename, bytes.length);
    }

    /**
     * Trust the file signature rather than a browser-supplied content type or
     * filename extension, so a renamed script cannot be stored as an image.
     */
    private String detectImageExtension(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47
                && bytes[4] == 0x0D && bytes[5] == 0x0A && bytes[6] == 0x1A && bytes[7] == 0x0A) {
            return "png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes.length >= 6) {
            String header = new String(bytes, 0, 6, java.nio.charset.StandardCharsets.US_ASCII)
                    .toUpperCase(Locale.ROOT);
            if (header.startsWith("GIF87A") || header.startsWith("GIF89A")) {
                return "gif";
            }
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
