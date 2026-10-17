package com.ledgerpilot.rate.service;

import com.ledgerpilot.common.calculator.service.RateConfigServiceInterface;
import com.ledgerpilot.common.entity.RateConfig;

import java.util.List;

public interface RateConfigService extends RateConfigServiceInterface {

    /**
     * 查询当前租户的全部费率配置
     * @param category 可选，按分类过滤
     */
    List<RateConfig> listByTenant(Long tenantId, String category);

    /**
     * 批量保存/更新费率配置（按 tenantId + category + subCategory 唯一键）
     * @param configs 费率配置列表
     * @param tenantId 租户ID
     */
    void saveBatch(List<RateConfig> configs, Long tenantId);

    /**
     * 查询指定租户+分类的单个配置
     * @param tenantId 租户ID
     * @param category 分类
     * @param subCategory 子分类（支持传null查该分类第一条enabled的配置）
     * @return 费率配置，未找到返回 null
     */
    @Override
    RateConfig getByCategory(Long tenantId, String category, String subCategory);
}
