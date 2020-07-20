package io.sitoolkit.rdg.core.domain.generator;

import io.sitoolkit.rdg.core.domain.generator.config.GeneratorConfig;
import io.sitoolkit.rdg.core.domain.schema.RelationDef;
import io.sitoolkit.rdg.core.domain.schema.UniqueConstraintDef;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainRelationDataGenerator extends RelationDataGenerator {

  public MainRelationDataGenerator(
      RelationDef relation, RowDataStore dataStoreForSubRel, GeneratorConfig config) {
    super(relation, dataStoreForSubRel, config);
  }

  @Override
  public void doGenerateAndFill(RowData rowData) {

    RowData existingData = rowData.filter(getRelation().getMainColumns());

    if (existingData.size() == getRelation().getMainColumns().size()) {
      // RowData subData = RowDataGenerator.replicateForSub(existingData, getRelation());

      // log.trace("Existing main data: {} is passed as sub data: {}", existingData, subData);

      // getDataStoreForSubRel().add(subData);

      log.trace("Existing main data: {} is passed as sub data", existingData);

      getDataStoreForSubRel().add(existingData);

      return;
    }

    RowData mainData = null;

    List<UniqueConstraintDef> uniques = getRelation().getMainUniqueConstraints();

    if (uniques.isEmpty()) {
      mainData = RowDataGenerator.append(rowData, getRelation(), getConfig());

    } else {

      Function<UniqueConstraintDef, RowData> function =
          unique -> RowDataGenerator.append(rowData, getRelation(), getConfig());

      mainData = RowDataGenerator.applyWithUniqueCheck(function, uniques, getUniqueDataStore());
    }

    rowData.putAll(mainData);
    // RowData subData = RowDataGenerator.replicateForSub(mainData, getRelation());

    // log.trace("Generated main data: {} is passed as sub data: {}", mainData, subData);

    // getDataStoreForSubRel().add(subData);

    log.trace("Generated main data: {} is passed as sub data", mainData);

    getDataStoreForSubRel().add(mainData);
  }

  @Override
  public void end() {
    getDataStoreForSubRel().setUp();
  }
}
