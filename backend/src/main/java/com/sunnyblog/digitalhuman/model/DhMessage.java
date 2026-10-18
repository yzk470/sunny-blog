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
@TableName("dh_message")
public class DhMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String role;
    private String content;
    private String emotion;
    private String audioUrl;
    private LocalDateTime createdAt;
}
