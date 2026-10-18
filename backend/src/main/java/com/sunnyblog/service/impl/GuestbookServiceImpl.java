package com.sunnyblog.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sunnyblog.common.BlogConstants;
import com.sunnyblog.common.BusinessException;
import com.sunnyblog.common.PageResult;
import com.sunnyblog.dto.GuestbookVO;
import com.sunnyblog.dto.request.GuestbookCreateRequest;
import com.sunnyblog.entity.Guestbook;
import com.sunnyblog.mapper.GuestbookMapper;
import com.sunnyblog.service.GuestbookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GuestbookServiceImpl implements GuestbookService {

    private final GuestbookMapper guestbookMapper;

    @Override
    public PageResult<GuestbookVO> listApproved(int page, int size) {
        IPage<Guestbook> result = guestbookMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)),
                Wrappers.lambdaQuery(Guestbook.class)
                        .eq(Guestbook::getStatus, BlogConstants.COMMENT_APPROVED)
                        .orderByDesc(Guestbook::getCreatedAt));
        List<GuestbookVO> records = result.getRecords().stream().map(g -> toVO(g, false)).toList();
        return PageResult.of(result, records);
    }

    @Override
    @Transactional
    public GuestbookVO create(GuestbookCreateRequest request) {
        Guestbook guestbook = new Guestbook();
        guestbook.setAuthor(request.author().trim());
        guestbook.setEmail(trimToNull(request.email()));
        guestbook.setContent(request.content().trim());
        guestbook.setStatus(BlogConstants.COMMENT_PENDING);
        guestbook.setCreatedAt(LocalDateTime.now());
        guestbookMapper.insert(guestbook);
        return toVO(guestbook, true);
    }

    @Override
    public PageResult<GuestbookVO> adminList(Integer status, int page, int size) {
        var query = Wrappers.lambdaQuery(Guestbook.class);
        if (status != null) {
            query.eq(Guestbook::getStatus, status);
        }
        query.orderByDesc(Guestbook::getCreatedAt);
        IPage<Guestbook> result = guestbookMapper.selectPage(new Page<>(Math.max(1, page), clampSize(size)), query);
        return PageResult.of(result, result.getRecords().stream().map(g -> toVO(g, true)).toList());
    }

    @Override
    @Transactional
    public void moderate(Long id, int status) {
        Guestbook guestbook = requireGuestbook(id);
        if (status < BlogConstants.COMMENT_PENDING || status > BlogConstants.COMMENT_REJECTED) {
            throw new BusinessException("留言状态不合法");
        }
        guestbook.setStatus(status);
        guestbookMapper.updateById(guestbook);
    }

    @Override
    @Transactional
    public void reply(Long id, String reply) {
        Guestbook guestbook = requireGuestbook(id);
        guestbook.setReply(reply == null ? null : reply.trim());
        guestbookMapper.updateById(guestbook);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireGuestbook(id);
        guestbookMapper.deleteById(id);
    }

    private Guestbook requireGuestbook(Long id) {
        Guestbook guestbook = guestbookMapper.selectById(id);
        if (guestbook == null) {
            throw new BusinessException(404, "留言不存在");
        }
        return guestbook;
    }

    private GuestbookVO toVO(Guestbook g, boolean adminView) {
        return new GuestbookVO(
                g.getId(),
                g.getAuthor(),
                adminView ? g.getEmail() : null,
                g.getContent(),
                g.getReply(),
                adminView ? g.getStatus() : null,
                g.getCreatedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int clampSize(int size) {
        return Math.max(1, Math.min(size, 50));
    }
}
