package com.ledgerpilot.rate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ledgerpilot.common.entity.RateConfig;
import com.ledgerpilot.rate.mapper.RateConfigMapper;
import com.ledgerpilot.rate.service.RateConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RateConfigServiceImpl implements RateConfigService {

    private static final Logger log = LoggerFactory.getLogger(RateConfigServiceImpl.class);

    private final RateConfigMapper rateConfigMapper;

    @Autowired
    public RateConfigServiceImpl(RateConfigMapper rateConfigMapper) {
        this.rateConfigMapper = rateConfigMapper;
    }

    @Override
    public List<RateConfig> listByTenant(Long tenantId, String category) {
        LambdaQueryWrapper<RateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RateConfig::getTenantId, tenantId);
        if (category != null && !category.trim().isEmpty()) {
            wrapper.eq(RateConfig::getCategory, category);
        }
        wrapper.orderByAsc(RateConfig::getCategory, RateConfig::getSubCategory);
        return rateConfigMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(List<RateConfig> configs, Long tenantId) {
        for (RateConfig cfg : configs) {
            cfg.setTenantId(tenantId);
            if (cfg.getUnit() == null) {
                cfg.setUnit("PERCENT");
            }

            LambdaQueryWrapper<RateConfig> existWrapper = new LambdaQueryWrapper<>();
            existWrapper.eq(RateConfig::getTenantId, tenantId)
                    .eq(RateConfig::getCategory, cfg.getCategory())
                    .eq(RateConfig::getSubCategory, cfg.getSubCategory());
            RateConfig existing = rateConfigMapper.selectOne(existWrapper);

            if (existing != null) {
                UpdateWrapper<RateConfig> uw = new UpdateWrapper<>();
                uw.eq("id", existing.getId());

                if (cfg.getPersonalRate() != null) {
                    uw.set("personal_rate", cfg.getPersonalRate());
                }
                if (cfg.getEmployerRate() != null) {
                    uw.set("employer_rate", cfg.getEmployerRate());
                }
                if (cfg.getFixedRate() != null) {
                    uw.set("fixed_rate", cfg.getFixedRate());
                }
                if (cfg.getUnit() != null) {
                    uw.set("unit", cfg.getUnit());
                }
                if (cfg.getDescription() != null) {
                    uw.set("description", cfg.getDescription());
                }
                if (cfg.getEnabled() != null) {
                    uw.set("enabled", cfg.getEnabled());
                }

                rateConfigMapper.update(null, uw);
                log.debug("更新费率配置 id={}, category={}, subCategory={}",
                        existing.getId(), cfg.getCategory(), cfg.getSubCategory());
            } else {
                rateConfigMapper.insert(cfg);
                log.debug("新增费率配置 category={}, subCategory={}",
                        cfg.getCategory(), cfg.getSubCategory());
            }
        }
    }

    @Override
    public RateConfig getByCategory(Long tenantId, String category, String subCategory) {
        LambdaQueryWrapper<RateConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RateConfig::getTenantId, tenantId)
                .eq(RateConfig::getCategory, category)
                .eq(RateConfig::getEnabled, 1);

        if (subCategory != null && !subCategory.trim().isEmpty()) {
            wrapper.eq(RateConfig::getSubCategory, subCategory);
        }

        wrapper.last("LIMIT 1");
        return rateConfigMapper.selectOne(wrapper);
    }
}
