import { listMyChartByPageUsingPost, updateFalseChartUsingPost } from '@/services/yubi/chartController';
import { useModel } from '@@/exports';
import { Avatar, Card, List, message, Result } from 'antd';
import ReactECharts from 'echarts-for-react';
import React, { useEffect, useState } from 'react';
import Search from "antd/es/input/Search";
import { Button } from 'antd';
import { request } from 'umi'; // 假设你使用 umi 的 request 方法

/**
 * 我的图表页面
 * @constructor
 */
const MyChartPage: React.FC = () => {
  const initSearchParams = {
    // 默认第一页
    current: 1,
    // 每页展示4条数据
    pageSize: 4,
    //设置排序规则
    sortField: 'createTime',
    //进行继续排序
    sortOrder: 'desc',
  };

  const [searchParams, setSearchParams] = useState<API.ChartQueryRequest>({ ...initSearchParams });
  // 从全局状态中获取到当前登录的用户信息
  const { initialState } = useModel('@@initialState');
  const { currentUser } = initialState ?? {};
  const [chartList, setChartList] = useState<API.Chart[]>();
  const [total, setTotal] = useState<number>(0);
  // 加载状态，用来控制页面是否加载，默认正在加载
  const [loading, setLoading] = useState<boolean>(true);

  const loadData = async () => {
    // 获取数据中,还在加载中,把loading设置为true
    setLoading(true);
    try {
      const res = await listMyChartByPageUsingPost(searchParams);
      if (res.data) {
        setChartList(res.data.records ?? []);
        setTotal(res.data.total ?? 0);
        // 有些图表有标题,有些没有,直接把标题全部去掉
        if (res.data.records) {
          res.data.records.forEach(data => {
            //只有任务执行完成才需要渲染图表
            if (data.status === 'succeed') {
              // 要把后端返回的图表字符串改为对象数组,如果后端返回空字符串，就返回'{}'
              const chartOption = JSON.parse(data.genChart ?? '{}');
              // 把标题设为undefined
              chartOption.title = undefined;
              // 然后把修改后的数据转换为json设置回去
              data.genChart = JSON.stringify(chartOption);
            }
          })
        }
      } else {
        message.error('获取我的图表失败');
      }
    } catch (e: any) {
      message.error('获取我的图表失败，' + e.message);
    }
    // 获取数据后，加载完毕，设置为false
    setLoading(false);
  };



 

  const handleRetry = async (item) => {
    message.success('图表重新生成中，请耐心等待，成功生成后会自动刷新页面');
    try {
      // 构造请求参数
      const chartAddRequest = {
        name: item.name,
        goal: item.goal,
        chartType: item.chartType,
        id: item.id,
        chartData: item.chartData,
      };
  
      // 调用后端 API
      const res = await request('/api/chart/gen/updateFalseChart', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json', // 明确指定 Content-Type
        },
        data: {
          ...chartAddRequest,
          id: item.id, // 将 id 作为请求体的一部分
        },
      });
  
      if (res.code === 0) {
        message.success('图表重新生成成功');
        // 如果需要刷新数据，可以重新加载数据
        loadData();
      } else {
        message.error('图表重新生成失败：' + res.message);
      }
    } catch (e) {
      message.error('请求失败：' + e.message);
    }
  };


  useEffect(() => {
    loadData();
  }, [searchParams]);

  return (
    <div className="my-chart-page">
      {/* 引入搜索框 */}
      <div style={{ display: 'flex', alignItems: 'center' }}>
        <Search placeholder="请输入图表名称" enterButton loading={loading} onSearch={(value) => {
          // 设置搜索条件
          setSearchParams({
            // 原始搜索条件
            ...initSearchParams,
            // 搜索词
            name: value,
          })
        }} />
        <Button style={{ marginLeft: 16 }} onClick={loadData}>刷新图表</Button>
      </div>
      <div className='margin-16' />
      <List
        /*
          栅格间隔16像素;xs屏幕<576px,栅格数1;
          sm屏幕≥576px，栅格数1;md屏幕≥768px,栅格数1;
          lg屏幕≥992px,栅格数2;xl屏幕≥1200px,栅格数2;
          xxl屏幕≥1600px,栅格数2
        */
        grid={{
          gutter: 16,
          xs: 1,
          sm: 1,
          md: 1,
          lg: 2,
          xl: 2,
          xxl: 2,
        }}
        pagination={{
          /*
            page第几页，pageSize每页显示多少条;
            当用户点击这个分页组件,切换分页时,这个组件就会去触发onChange方法,会改变咱们现在这个页面的搜索条件
          */
          onChange: (page, pageSize) => {
            // 当切换分页，在当前搜索条件的基础上，把页数调整为当前的页数
            setSearchParams({
              ...searchParams,
              current: page,
              pageSize,
            })
          },
          // 显示当前页数
          current: searchParams.current,
          // 页面参数改成自己的
          pageSize: searchParams.pageSize,
          // 总数设置成自己的
          total: total,
        }}
        // 设置成我们的加载状态
        loading={loading}
        dataSource={chartList}
        renderItem={(item) => (
          <List.Item key={item.id}>
            {/* 用卡片包裹 */}
            <Card style={{ width: '100%' }}>
              <List.Item.Meta
                // 把当前登录用户信息的头像展示出来
                avatar={<Avatar src={currentUser && currentUser.userAvatar} />}
                title={item.name}
                description={item.chartType ? '图表类型：' + item.chartType : undefined}
              />


              <>
                {
                  // 当状态（item.status）为'wait'时，显示待生成的结果组件
                  item.status === 'wait' && <>
                    <Result
                      // 状态为警告
                      status="warning"
                      title="待生成"
                      // 子标题显示执行消息，如果执行消息为空，则显示'当前图表生成队列繁忙，请耐心等候'
                      subTitle={item.execMessage ?? '当前图表生成队列繁忙，请耐心等候'}
                    />
                  </>
                }
                {
                  item.status === 'running' && <>
                    <Result
                      // 状态为信息
                      status="info"
                      title="图表生成中"
                      // 子标题显示执行消息
                      subTitle={item.execMessage}
                    />
                  </>
                }
                {
                  // 当状态（item.status）为'succeed'时，显示生成的图表
                  item.status === 'succeed' && <>
                    <div style={{ marginBottom: 16 }} />
                    <p>{'分析目标：' + item.goal}</p>
                    <div style={{ marginBottom: 16 }} />
                    <p>{'分析结论：' + item.genResult}</p>
                    <ReactECharts option={item.genChart && JSON.parse(item.genChart)} />
                  </>
                }
                {
                  // 当状态（item.status）为'failed'时，显示生成失败的结果组件
                  item.status === 'failed' && <>
                    <Result
                      status="error"
                      title="图表生成失败"
                      subTitle={item.execMessage}
                    />
                  </>
                }
                {
                  item.status === 'failed' && (
                    <Button
                      type="primary"
                      onClick={async () => {
                        await handleRetry(item); // 等待重新生成逻辑完成
                        loadData(); // 重新加载数据
                      }}
                    >
                      重新生成
                    </Button>
                  )
                }
              </>

            </Card>
          </List.Item>
        )}
      />
    </div>
  );
};
export default MyChartPage;
