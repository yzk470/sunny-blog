package com.sunnyblog.service;

import com.sunnyblog.dto.DashboardVO;
import com.sunnyblog.dto.SiteProfileVO;
import com.sunnyblog.dto.SiteStatsVO;
import com.sunnyblog.dto.request.SiteProfileRequest;

public interface SiteService {

    SiteProfileVO getProfile();

    SiteStatsVO getStats();

    DashboardVO getDashboard();

    SiteProfileVO updateProfile(SiteProfileRequest request);
}
