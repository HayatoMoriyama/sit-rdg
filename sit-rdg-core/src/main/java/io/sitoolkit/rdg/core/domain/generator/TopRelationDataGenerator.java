package io.sitoolkit.rdg.core.domain.generator;

import io.sitoolkit.rdg.core.domain.generator.config.GeneratorConfig;
import io.sitoolkit.rdg.core.domain.schema.RelationDef;

public class TopRelationDataGenerator extends RelationDataGenerator {

  public TopRelationDataGenerator(
      RelationDef relation, RowDataStore dataStoreForSubRel, GeneratorConfig config) {
    super(relation, dataStoreForSubRel, config);
  }

  @Override
  public void doGenerateAndFill(RowData rowData) {
    // RowData subData = RowDataGenerator.replicateForSub(rowData, getRelation());
    // getDataStoreForSubRel().add(subData);
    getDataStoreForSubRel().add(rowData);
  }

  @Override
  public void end() {
    getDataStoreForSubRel().setUp();
  }
}
