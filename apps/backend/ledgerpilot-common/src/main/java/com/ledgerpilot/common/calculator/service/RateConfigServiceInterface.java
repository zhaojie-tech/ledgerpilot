package com.ledgerpilot.common.calculator.service;

import com.ledgerpilot.common.entity.RateConfig;

import java.util.List;

/**
 * 费率配置查询接口
 * 定义于 ledgerpilot-common，由 ledgerpilot-admin 的 RateConfigServiceImpl 实现
 *
 * 所有计算服务（工资、税务）均通过此接口获取费率配置，
 * 避免直接依赖 ledgerpilot-admin 模块，保持模块解耦
 */
public interface RateConfigServiceInterface {

    /**
     * 查询指定租户的全部费率配置
     * @param tenantId 租户ID
     * @param category 可选，按分类过滤（social/housing/vat/corp_tax/depreciation）
     * @return 费率配置列表（仅返回 enabled=1 的记录）
     */
    List<RateConfig> listByTenant(Long tenantId, String category);

    /**
     * 查询指定租户+分类的单个配置
     * @param tenantId 租户ID
     * @param category 分类
     * @param subCategory 子分类
     * @return 费率配置，未找到返回 null
     */
    RateConfig getByCategory(Long tenantId, String category, String subCategory);
}
