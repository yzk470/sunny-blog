package com.sunnyblog.digitalhuman.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.digitalhuman.dto.DhMessageVO;
import com.sunnyblog.digitalhuman.dto.DhSessionVO;
import com.sunnyblog.digitalhuman.mapper.DhMessageMapper;
import com.sunnyblog.digitalhuman.mapper.DhSessionMapper;
import com.sunnyblog.digitalhuman.model.DhCharacter;
import com.sunnyblog.digitalhuman.model.DhMessage;
import com.sunnyblog.digitalhuman.model.DhSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DigitalHumanSessionService {

    private final DhSessionMapper sessionMapper;
    private final DhMessageMapper messageMapper;
    private final DigitalHumanCharacterService characterService;

    @Transactional
    public DhSession create(String characterCode) {
        DhCharacter character = characterService.getByCode(characterCode);
        if (character == null) {
            throw new BusinessException("这个数字人角色暂时不可用");
        }
        LocalDateTime now = LocalDateTime.now();
        DhSession session = DhSession.builder()
                .sessionName("与" + character.getCharacterName() + "的对话")
                .characterCode(character.getCharacterCode())
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();
        sessionMapper.insert(session);
        return session;
    }

    public DhSession requireSession(Long sessionId) {
        DhSession session = sessionId == null ? null : sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "会话已经结束，请开启新的对话");
        }
        return session;
    }

    public DhSessionVO getSessionVO(Long sessionId) {
        DhSession session = requireSession(sessionId);
        DhCharacter character = characterService.getByCode(session.getCharacterCode());
        List<DhMessageVO> messages = history(sessionId, 0).stream().map(this::toVO).toList();
        return new DhSessionVO(
                session.getId(),
                session.getCharacterCode(),
                character == null ? session.getCharacterCode() : character.getCharacterName(),
                session.getStatus(),
                session.getCreatedAt(),
                messages
        );
    }

    public List<DhMessage> history(Long sessionId, int limit) {
        var query = Wrappers.lambdaQuery(DhMessage.class)
                .eq(DhMessage::getSessionId, sessionId)
                .orderByDesc(DhMessage::getCreatedAt)
                .orderByDesc(DhMessage::getId);
        if (limit > 0) {
            query.last("limit " + Math.max(1, Math.min(limit, 30)));
        }
        List<DhMessage> messages = new ArrayList<>(messageMapper.selectList(query));
        Collections.reverse(messages);
        return messages;
    }

    @Transactional
    public DhMessage saveUserMessage(Long sessionId, String content) {
        DhMessage message = DhMessage.builder()
                .sessionId(sessionId)
                .role("USER")
                .content(content.trim())
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(message);
        touch(sessionId);
        return message;
    }

    @Transactional
    public DhMessage saveAssistantMessage(Long sessionId, String content, String emotion, String audioUrl) {
        DhMessage message = DhMessage.builder()
                .sessionId(sessionId)
                .role("ASSISTANT")
                .content(content.trim())
                .emotion(emotion)
                .audioUrl(audioUrl)
                .createdAt(LocalDateTime.now())
                .build();
        messageMapper.insert(message);
        touch(sessionId);
        return message;
    }

    @Transactional
    public void delete(Long sessionId) {
        requireSession(sessionId);
        messageMapper.delete(Wrappers.lambdaQuery(DhMessage.class).eq(DhMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
    }

    private void touch(Long sessionId) {
        DhSession session = sessionMapper.selectById(sessionId);
        if (session != null) {
            session.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(session);
        }
    }

    private DhMessageVO toVO(DhMessage message) {
        return new DhMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getEmotion(),
                message.getAudioUrl(),
                message.getCreatedAt()
        );
    }
}
