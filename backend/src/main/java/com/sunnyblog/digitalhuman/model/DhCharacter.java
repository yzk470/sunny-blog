package com.sunnyblog.digitalhuman.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("dh_character")
public class DhCharacter {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String characterCode;
    private String characterName;
    private String avatarIcon;
    private String systemPrompt;
    private String ttsVoice;
    private String ttsModel;
    private Integer ttsSampleRate;
    private String modelPath;
    private String greetingMessage;
    private Integer sortOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String fullModelPath() {
        if (modelPath == null || modelPath.isBlank()) {
            return null;
        }
        return "/models/" + modelPath.replace("\\", "/");
    }

    public boolean active() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
