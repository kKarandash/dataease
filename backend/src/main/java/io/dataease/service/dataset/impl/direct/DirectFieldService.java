package io.dataease.service.dataset.impl.direct;

import com.google.gson.Gson;
import io.dataease.base.domain.DatasetTable;
import io.dataease.base.domain.DatasetTableField;
import io.dataease.base.domain.Datasource;
import io.dataease.commons.utils.CommonBeanFactory;
import io.dataease.dto.chart.ChartFieldCustomFilterDTO;
import io.dataease.i18n.Translator;
import io.dataease.provider.datasource.DatasourceProvider;
import io.dataease.provider.ProviderFactory;
import io.dataease.controller.request.datasource.DatasourceRequest;
import io.dataease.service.datasource.DatasourceService;
import io.dataease.dto.dataset.DataSetTableUnionDTO;
import io.dataease.dto.dataset.DataTableInfoDTO;
import io.dataease.provider.query.QueryProvider;
import io.dataease.service.dataset.DataSetFieldService;
import io.dataease.service.dataset.DataSetTableFieldsService;
import io.dataease.service.dataset.DataSetTableService;
import io.dataease.service.dataset.DataSetTableUnionService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Service("directDataSetFieldService")
public class DirectFieldService implements DataSetFieldService {
    @Resource
    private DataSetTableFieldsService dataSetTableFieldsService;
    @Resource
    private DataSetTableService dataSetTableService;
    @Resource
    private DatasourceService datasourceService;
    @Resource
    private DataSetTableUnionService dataSetTableUnionService;

    @Override
    public List<Object> fieldValues(String fieldId, Long userId) throws Exception{
        List<DatasetTableField> list = dataSetTableFieldsService.getListByIds(new ArrayList<String>() {{
            add(fieldId);
        }});
        if (CollectionUtils.isEmpty(list)) return null;

        DatasetTableField field = list.get(0);
        String tableId = field.getTableId();
        if (StringUtils.isEmpty(tableId)) return null;

        DatasetTable datasetTable = dataSetTableService.get(tableId);
        if (ObjectUtils.isEmpty(datasetTable) || StringUtils.isEmpty(datasetTable.getName())) return null;
        String tableName;

        DatasetTableField datasetTableField = DatasetTableField.builder().tableId(tableId).checked(Boolean.TRUE).build();
        List<DatasetTableField> fields = dataSetTableFieldsService.list(datasetTableField);
        List<ChartFieldCustomFilterDTO> customFilter = dataSetTableService.getCustomFilters(fields, datasetTable, userId);

        DatasourceRequest datasourceRequest = new DatasourceRequest();
        DatasourceProvider datasourceProvider = null;
        if (datasetTable.getMode() == 0) {// 直连
            if (StringUtils.isEmpty(datasetTable.getDataSourceId())) return null;
            Datasource ds = datasourceService.get(datasetTable.getDataSourceId());
            if(StringUtils.isNotEmpty(ds.getStatus()) && ds.getStatus().equalsIgnoreCase("Error")){
                throw new Exception(Translator.get("i18n_invalid_ds"));
            }
            datasourceProvider = ProviderFactory.getProvider(ds.getType());
            datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(ds);
            DataTableInfoDTO dataTableInfoDTO = new Gson().fromJson(datasetTable.getInfo(), DataTableInfoDTO.class);
            QueryProvider qp = ProviderFactory.getQueryProvider(ds.getType());
            if (StringUtils.equalsIgnoreCase(datasetTable.getType(), "db")) {
                datasourceRequest.setTable(dataTableInfoDTO.getTable());
                datasourceRequest.setQuery(qp.createQuerySQL(dataTableInfoDTO.getTable(), Collections.singletonList(field), true, ds, customFilter));
            } else if (StringUtils.equalsIgnoreCase(datasetTable.getType(), "sql")) {
                datasourceRequest.setQuery(qp.createQuerySQLAsTmp(dataTableInfoDTO.getSql(), Collections.singletonList(field), true, customFilter));
            } else if (StringUtils.equalsIgnoreCase(datasetTable.getType(), "custom")) {
                DataTableInfoDTO dt = new Gson().fromJson(datasetTable.getInfo(), DataTableInfoDTO.class);
                List<DataSetTableUnionDTO> listUnion = dataSetTableUnionService.listByTableId(dt.getList().get(0).getTableId());
                String sql = dataSetTableService.getCustomSQLDatasource(dt, listUnion, ds);
                datasourceRequest.setQuery(qp.createQuerySQLAsTmp(sql, Collections.singletonList(field), true, customFilter));
            } else if (StringUtils.equalsIgnoreCase(datasetTable.getType(), "union")) {
                DataTableInfoDTO dt = new Gson().fromJson(datasetTable.getInfo(), DataTableInfoDTO.class);
                String sql = (String) dataSetTableService.getUnionSQLDatasource(dt, ds).get("sql");
                datasourceRequest.setQuery(qp.createQuerySQLAsTmp(sql, Collections.singletonList(field), true, customFilter));
            }
        } else if (datasetTable.getMode() == 1) {// 抽取
            // 连接doris，构建doris数据源查询
            Datasource ds = (Datasource) CommonBeanFactory.getBean("DorisDatasource");
            datasourceProvider = ProviderFactory.getProvider(ds.getType());
            datasourceRequest = new DatasourceRequest();
            datasourceRequest.setDatasource(ds);
            tableName = "ds_" + datasetTable.getId().replaceAll("-", "_");
            datasourceRequest.setTable(tableName);
            QueryProvider qp = ProviderFactory.getQueryProvider(ds.getType());
            datasourceRequest.setQuery(qp.createQuerySQL(tableName, Collections.singletonList(field), true, null, customFilter));
        }

        List<String[]> rows = datasourceProvider.getData(datasourceRequest);
        List<Object> results = rows.stream().map(row -> row[0]).distinct().collect(Collectors.toList());
        return results;
    }
}
