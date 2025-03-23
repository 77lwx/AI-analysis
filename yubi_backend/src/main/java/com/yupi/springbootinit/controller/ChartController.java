package com.yupi.springbootinit.controller;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;

import cn.hutool.core.lang.Singleton;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.springbootinit.annotation.AuthCheck;
import com.yupi.springbootinit.bizmq.BiMessageProducer;
import com.yupi.springbootinit.common.BaseResponse;
import com.yupi.springbootinit.common.DeleteRequest;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.common.ResultUtils;
import com.yupi.springbootinit.constant.CommonConstant;
import com.yupi.springbootinit.constant.UserConstant;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.exception.ThrowUtils;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.model.dto.chart.*;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.entity.User;
import com.yupi.springbootinit.model.vo.BiResponse;
import com.yupi.springbootinit.service.ChartService;
import com.yupi.springbootinit.service.UserService;
import com.yupi.springbootinit.utils.ExcelUtils;
import com.yupi.springbootinit.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

/**
 * 帖子接口
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private RedisLimiterManager redisLimiterManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;


    @Resource
    private BiMessageProducer biMessageProducer;

    private static final String EDITING = "edit:%s";

    private final static String EDIT_LUA_PATH = "lua/editing.lua";



    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();

        return ResultUtils.success(newChartId);
    }


    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();//获取当前页号
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
        getQueryWrapper(chartQueryRequest));

//        Page<Chart> objectPage = Page.of(current, size);
//        Page<Chart> chartPage = chartService.page(objectPage);

        return ResultUtils.success(chartPage);
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                       HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }


    /**
     * 智能分析(同步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<BiResponse> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                                 GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        String editKey = String.format(EDITING, loginUser.getId());
        DefaultRedisScript<Boolean> buildLuaScript = Singleton.get(EDIT_LUA_PATH, () -> {
            DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(EDIT_LUA_PATH)));
            redisScript.setResultType(Boolean.class);
            return redisScript;
        });

        Boolean luaResult = stringRedisTemplate.execute(
                buildLuaScript,
                Collections.singletonList(editKey)
        );

        if(!luaResult){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已达到最大任务数");
        }

        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //限制文件大小
        long maxsize = 1024 * 1024L;
        long filesize = multipartFile.getSize();
        ThrowUtils.throwIf(filesize > maxsize, ErrorCode.PARAMS_ERROR, "文件大小超过1MB");

        //限制文件后缀
        List<String> witheList = Arrays.asList("xlsx", "xls");
        //获取文件名
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!witheList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");

        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAi_" + loginUser.getId());

        //指定一个模型id，写死
        long modelId = 1825519621357690882L;
        //用户输入
//        分析需求：
//        分析网站用户的增长情况
//        原始数据:
//        日期,用户数
//        1号,10
//        2号,20
//        3号,30
        //构造输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");
        String userGoal = goal;
        //如果有需求图表类型，拼接在需求后面
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用" + chartType + "分析";
        }
        userInput.append(userGoal);
        userInput.append("原始数据:").append("\n");
        //将 Excel 文件转换为 CSV 数据
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //拿到了返回结果
        String result = aiManager.doChat(modelId, userInput.toString());
        stringRedisTemplate.opsForValue().increment(editKey, -1);

        //对返回的结果以五个中括号分隔
        String[] split = result.split("【【【【【");
        String genChart = split[1];
        String genResult = split[2];
        if(StringUtils.isAnyBlank(genChart,genResult)){
            Chart chart = new Chart();
            chart.setName(name);
            chart.setGoal(goal);
            chart.setChartData(csvData);
            chart.setChartType(chartType);
            chart.setUserId(loginUser.getId());
            chart.setStatus("false");
            boolean save = chartService.save(chart);
            ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "数据保存失败");
        }
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        chart.setStatus("succeed");
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "数据保存失败");

        //封装BiResponse
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(loginUser.getId());
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        return ResultUtils.success(biResponse);
    }


    /**
     * 重新生成表单
     *
     * @return
     */

    @PostMapping("/gen/updateFalseChart")
    public BaseResponse<BiResponse> updateFalseChart(@RequestBody ChartAgainRequest chartAgainRequest,
                                                     HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);


        Chart oldChart = chartService.getById(chartAgainRequest.getId());
        if (!"failed".equals(oldChart.getStatus())) {
            //没有生成失败，无法重新生成
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "无法重新生成");
        }
        oldChart.setStatus("wait");
        chartService.updateById(oldChart);
        String goal = chartAgainRequest.getGoal();
        String chartType = chartAgainRequest.getChartType();
        String csvData = chartAgainRequest.getChartData();
        long modelId = 1825519621357690882L;
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");
        String userGoal = goal;
        //如果有需求图表类型，拼接在需求后面
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用" + chartType + "分析";
        }
        userInput.append(userGoal);
        userInput.append("原始数据:").append("\n");
        userInput.append(csvData).append("\n");
        //拿到了返回结果
        String result = aiManager.doChat(modelId, userInput.toString());


        //对返回的结果以五个中括号分隔
        String[] split = result.split("【【【【【");
        String genChart = split[1];
        String genResult = split[2];
        if(StringUtils.isAnyBlank(genChart,genResult)){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "数据生成失败");
        }
        oldChart.setGenChart(genChart);
        oldChart.setGenResult(genResult);
        oldChart.setStatus("succeed");
        boolean save = chartService.updateById(oldChart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "数据更新失败");
        //封装BiResponse
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(loginUser.getId());
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析(异步)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                                      GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {


        User loginUser = userService.getLoginUser(request);
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        String editKey = String.format(EDITING, loginUser.getId());
        DefaultRedisScript<Boolean> buildLuaScript = Singleton.get(EDIT_LUA_PATH, () -> {
            DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(EDIT_LUA_PATH)));
            redisScript.setResultType(Boolean.class);
            return redisScript;
        });

        Boolean luaResult = stringRedisTemplate.execute(
                buildLuaScript,
                Collections.singletonList(editKey)
        );

        if(!luaResult){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已达到最大任务数");
        }


        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //限制文件大小
        long maxsize = 1024 * 1024L;
        long filesize = multipartFile.getSize();
        ThrowUtils.throwIf(filesize > maxsize, ErrorCode.PARAMS_ERROR, "文件大小超过1MB");

        //限制文件后缀
        List<String> witheList = Arrays.asList("xlsx", "xls");
        //获取文件名
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!witheList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAiAsync_" + loginUser.getId());


        //指定一个模型id，写死
        long modelId = 1825519621357690882L;
        //用户输入
//        分析需求：
//        分析网站用户的增长情况
//        原始数据:
//        日期,用户数
//        1号,10
//        2号,20
//        3号,30
        //构造输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求:").append("\n");
        String userGoal = goal;
        //如果有需求图表类型，拼接在需求后面
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += ",请使用" + chartType + "分析";
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据:").append("\n");
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //分析之后优先加入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "数据保存失败");

        // 异步执行任务
        CompletableFuture.runAsync(() -> {
            // 使用 CompletableFuture 执行 AI 任务，并设置超时时间
            CompletableFuture<String> aiFuture = CompletableFuture.supplyAsync(() -> {
                return aiManager.doChat(modelId, userInput.toString());
            }, threadPoolExecutor);

            try {
                // 设置超时时间为 30 秒
                String result = aiFuture.get(30, TimeUnit.SECONDS);
                stringRedisTemplate.opsForValue().increment(editKey, -1);
                // 对返回的结果以五个中括号分隔
                String[] split = result.split("【【【【【");
                if (split.length < 3) {
                handleChartUpdateError(chart.getId(), "图表分析内容失败，请重新生成");
                    return;
                }
                String genChart = split[1];
                String genResult = split[2];
                // 调用AI得到结果之后,再更新一次
                Chart updateChartResult = new Chart();
                updateChartResult.setId(chart.getId());
                updateChartResult.setGenChart(genChart);
                updateChartResult.setGenResult(genResult);
                updateChartResult.setStatus("succeed");
                boolean updateResult = chartService.updateById(updateChartResult);
                if (!updateResult) {
                    chartService.handleChartUpdateError(chart.getId(), "图表生成异常");
                }
            } catch (TimeoutException e) {
                // 超时处理逻辑
                aiFuture.cancel(true); // 取消任务
                chartService.handleChartUpdateError(chart.getId(), "图表生成超时");
                stringRedisTemplate.opsForValue().increment(editKey, -1);
            } catch (InterruptedException | ExecutionException e) {
                chartService.handleChartUpdateError(chart.getId(), "图表获取数据内容失败");
                stringRedisTemplate.opsForValue().increment(editKey, -1);
            }
        }, threadPoolExecutor);

        // 封装 BiResponse, 优先给前端用户返回任务信息
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId()); // 返回图表的 ID，而不是用户 ID
        return ResultUtils.success(biResponse);
    }


    /**
     * 智能分析(异步消息队列)
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile,
                                                        GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        String editKey = String.format(EDITING, loginUser.getId());
        DefaultRedisScript<Boolean> buildLuaScript = Singleton.get(EDIT_LUA_PATH, () -> {
            DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(EDIT_LUA_PATH)));
            redisScript.setResultType(Boolean.class);
            return redisScript;
        });

        Boolean luaResult = stringRedisTemplate.execute(
                buildLuaScript,
                Collections.singletonList(editKey)
        );

        if(!luaResult){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户已达到最大任务数");
        }


        //校验
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "目标为空");
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称过长");

        //限制文件大小
        long maxsize = 1024 * 1024L;
        long filesize = multipartFile.getSize();
        ThrowUtils.throwIf(filesize > maxsize, ErrorCode.PARAMS_ERROR, "文件大小超过1MB");

        //限制文件后缀
        //后缀白名单
        List<String> witheList = Arrays.asList("xlsx", "xls");
        //获取文件名
        String originalFilename = multipartFile.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        ThrowUtils.throwIf(!witheList.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 限流判断，每个用户一个限流器
        redisLimiterManager.doRateLimit("genChartByAiAsync_" + loginUser.getId());
        //指定一个模型id，写死
        long modelId = CommonConstant.BI_MODEL_ID;
        //用户输入
//        分析需求：
//        分析网站用户的增长情况
//        原始数据:
//        日期,用户数
//        1号,10
//        2号,20
//        3号,30

//        用于将Excel文件（在这个例子中是multipartFile，通常是一个上传的文件）转换为CSV格式的字符串。
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        //分析之后优先加入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean save = chartService.save(chart);
        ThrowUtils.throwIf(!save, ErrorCode.SYSTEM_ERROR, "数据保存失败");

        long newChartId = chart.getId();

        //往消息队列发消息
        biMessageProducer.sendMessage(String.valueOf(newChartId));

        //封装BiResponse,优先给前端用户返回任务信息就行
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(loginUser.getId());
        return ResultUtils.success(biResponse);
    }


    // 上面的接口很多用到异常,直接定义一个工具类
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }


    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String goal = chartQueryRequest.getGoal();
        String name = chartQueryRequest.getName();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }


}
