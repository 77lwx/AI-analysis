package com.yupi.springbootinit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.mapper.ChartMapper;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 *
 */
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{


    /**
     * 处理图表更新错误
     *
     * @param chartId 图表 ID
     * @param message 错误信息
     */
    public void handleChartUpdateError(long chartId, String message) {
        Chart oldChart = this.getById(chartId);
        oldChart.setStatus("failed");
        oldChart.setGenResult(message);
        this.updateById(oldChart);
    }

}





