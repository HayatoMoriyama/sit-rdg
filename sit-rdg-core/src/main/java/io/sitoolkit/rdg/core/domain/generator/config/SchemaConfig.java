package io.sitoolkit.rdg.core.domain.generator.config;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class SchemaConfig {

  @JsonBackReference private GeneratorConfig setting;

  @JsonProperty("schemaName")
  private String name;

  @JsonProperty("rowCount")
  private Long rowCount;

  @JsonProperty("requiredValueCount")
  private Integer requiredValueCount;

  @JsonManagedReference
  @JsonProperty("tableConfigs")
  private SortedSet<TableConfig> tableConfigs = new TreeSet<>();

  @JsonProperty private List<RelationConfig> relationConfigs = new ArrayList<>();

  public Long getRowCount() {
    if (Objects.isNull(rowCount)) {
      rowCount = setting.getDefaultRowCount();
    }
    return rowCount;
  }

  public Integer getRequiredValueCount() {
    if (Objects.isNull(requiredValueCount)) {
      requiredValueCount = setting.getDefaultRequiredValueCount();
    }
    return requiredValueCount;
  }
}
