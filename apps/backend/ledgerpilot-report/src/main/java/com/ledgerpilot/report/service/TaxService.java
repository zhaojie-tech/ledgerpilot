package com.ledgerpilot.report.service;

import com.ledgerpilot.report.vo.TaxVO;

import java.util.List;
import java.util.Map;

/**
 * 税务服务接口
 */
public interface TaxService {

    /**
     * 获取税务总览（各税种汇总）
     */
    List<TaxVO> getTaxSummary(int year, int period);

    /**
     * 增值税明细
     */
    TaxVO getVAT(int year, int period);

    /**
     * 企业所得税明细
     */
    TaxVO getCorporateIncomeTax(int year, int period);

    /**
     * 税金及附加（城建税/教育费附加/地方教育附加）
     */
    TaxVO getSurtax(int year, int period);

    /**
     * 获取有税务数据的可用期间
     */
    List<Map<String, Object>> getAvailablePeriods();
}
