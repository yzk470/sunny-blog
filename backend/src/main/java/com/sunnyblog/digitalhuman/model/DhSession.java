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
@TableName("dh_session")
public class DhSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionName;
    private String characterCode;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
