package com.yupi.springbootinit.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ExcelUtils {
    public static String excelToCsv(MultipartFile multipartFile){
//        File file = null;
//        try {
//            file = ResourceUtils.getFile("classpath:test.xlsx");
//        } catch (FileNotFoundException e) {
//            throw new RuntimeException(e);
//        }
        //读取数据
        List<Map<Integer, String>> list=null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream())
                       .excelType(ExcelTypeEnum.XLSX)
                       .sheet()
                       .headRowNumber(0)
                       .doReadSync();
        } catch (IOException e) {
            log.error("表格处理错误");
        }
        //如果表格为空
        if(list.isEmpty()){
            return "";
        }
        //转换成CSV
        StringBuilder stringBuilder=new StringBuilder();
        //获取表头
        LinkedHashMap<Integer, String> headerMap =(LinkedHashMap<Integer, String>) list.get(0);
        List<String> headList = headerMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
        //StringUtils.join方法用于将列表中的元素按指定的分隔符连接起来。
        stringBuilder.append(StringUtils.join(headList,",")).append("\n");

        //获取表数据
        for(int i=1;i<list.size();i++){
            LinkedHashMap<Integer, String> dataMap =(LinkedHashMap<Integer, String>) list.get(i);
            List<String> dataList = dataMap.values().stream().filter(ObjectUtils::isNotEmpty).collect(Collectors.toList());
            stringBuilder.append(StringUtils.join(dataList,",")).append("\n");
        }

        System.out.println(list);
        return stringBuilder.toString();
    }

    public static void main(String[] args) {
        excelToCsv(null);
    }

}
