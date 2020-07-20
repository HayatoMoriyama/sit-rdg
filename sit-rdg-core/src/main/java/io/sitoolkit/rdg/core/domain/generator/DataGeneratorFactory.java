package io.sitoolkit.rdg.core.domain.generator;

import io.sitoolkit.rdg.core.domain.generator.config.ColumnConfig;
import io.sitoolkit.rdg.core.domain.generator.config.ColumnConfig.InheritanceType;
import io.sitoolkit.rdg.core.domain.generator.config.GeneratorConfig;
import io.sitoolkit.rdg.core.domain.generator.config.IllegalConfigException;
import io.sitoolkit.rdg.core.domain.generator.config.RelationConfig;
import io.sitoolkit.rdg.core.domain.generator.config.TableConfig;
import io.sitoolkit.rdg.core.domain.generator.config.UniqueConstraintConfig;
import io.sitoolkit.rdg.core.domain.schema.ColumnDef;
import io.sitoolkit.rdg.core.domain.schema.DataTypeName;
import io.sitoolkit.rdg.core.domain.schema.RelationDef;
import io.sitoolkit.rdg.core.domain.schema.TableDef;
import io.sitoolkit.rdg.core.domain.schema.TableSorter;
import io.sitoolkit.rdg.core.domain.schema.UniqueConstraintDef;
import io.sitoolkit.rdg.core.domain.value.CharGenerator;
import io.sitoolkit.rdg.core.domain.value.DataTypeValueGenerator;
import io.sitoolkit.rdg.core.domain.value.DateGenerator;
import io.sitoolkit.rdg.core.domain.value.DecimalGenerator;
import io.sitoolkit.rdg.core.domain.value.IntegerGenerator;
import io.sitoolkit.rdg.core.domain.value.RandomGenerator;
import io.sitoolkit.rdg.core.domain.value.TimestampGenerator;
import io.sitoolkit.rdg.core.domain.value.ValueGenerator;
import io.sitoolkit.rdg.core.domain.value.VarcharGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DataGeneratorFactory {

  public static List<TableDataGenerator> build(
      final List<TableDef> tables, final GeneratorConfig config) {

    final List<TableDataGenerator> generators = new ArrayList<>();
    final Map<RelationDef, RowDataStore> relStoreMap = new HashMap<>();

    List<TableDef> filteredTables = config.filter(tables);
    final List<TableDef> sortedTables = TableSorter.sortByDependency(filteredTables);

    for (final TableDef table : sortedTables) {

      if (table.isDependent()) {
        generators.add(buildForDependent(table, config, relStoreMap));
      } else {
        generators.add(buildForIndependent(table, config, relStoreMap));
      }

      removeUniqueConstraints(table, config);

      registerColumnValueGenerator(table, config);
    }

    if (log.isDebugEnabled()) {
      for (final TableDataGenerator generator : generators) {
        log.debug(
            "{} is registered for {}",
            generator.getClass().getSimpleName(),
            generator.getTableName());

        for (final RelationDataGenerator relGenerator : generator.getGenerators()) {
          log.debug(
              "{}({}@{}) is registered for {}",
              relGenerator.getClass().getSimpleName(),
              relGenerator.getDataStoreForSubRel().getClass().getSimpleName(),
              System.identityHashCode(relGenerator.getDataStoreForSubRel()),
              relGenerator.getRelation());
        }
      }
    }

    return generators;
  }

  static TableDataGenerator buildForIndependent(
      final TableDef table,
      final GeneratorConfig config,
      final Map<RelationDef, RowDataStore> relStoreMap) {
    final TableDataGenerator generator = new IndependentTableDataGenerator(table, config);

    for (final RelationDef relation : table.getRelations()) {
      final RowDataStore dataStore = buildDataStore(relation, config);
      final RelationDataGenerator relGen =
          new TopRelationDataGenerator(relation, dataStore, config);
      relStoreMap.put(relation, dataStore);
      generator.add(relGen);
    }

    return generator;
  }

  static TableDataGenerator buildForDependent(
      final TableDef table,
      final GeneratorConfig config,
      final Map<RelationDef, RowDataStore> relStoreMap) {
    final TableDataGenerator generator = new DependendTableDataGenerator(table, config);

    for (final RelationDef relation : table.getSubRelations()) {
      final RowDataStore dataStore = relStoreMap.get(relation);
      if (dataStore == null) {
        // TODO avoid hard coding "generator-config.json"
        throw new IllegalConfigException(
            relation.getMainTable() + " must be listed on generator-config.json");
      }
      generator.add(new SubRelationDataGenerator(relation, dataStore, config));
    }

    for (final RelationDef relation : table.getSelfRelations()) {
      final RowDataStore dataStore =
          relStoreMap.computeIfAbsent(relation, rel -> new RowDataStoreImpl());
      generator.add(new SelfRelationDataGenerator(relation, dataStore, config));
    }

    for (final RelationDef relation : table.getMainRelations()) {
      if (!config.isGenerationTarget(relation.getSubTable())) {
        continue;
      }
      final RowDataStore dataStore =
          relStoreMap.computeIfAbsent(relation, rel -> buildDataStore(relation, config));
      generator.add(new MainRelationDataGenerator(relation, dataStore, config));
    }

    return generator;
  }

  static RowDataStore buildDataStore(final RelationDef relation, final GeneratorConfig config) {

    if (config.isListedOnly() && !config.contains(relation.getSubTable())) {
      return NopRowDataStore.INSTANCE;
    }

    ColumnDef mainColumn = relation.getMainColumns().get(0);
    InheritanceType inheritanceType =
        config.findColumnInheritanceType(mainColumn.getFullyQualifiedName());
    if (InheritanceType.RULE.equals(inheritanceType)) {

      Optional<RelationConfig> rconfig = config.findRelationConfig(relation.getSubColumns());

      if (rconfig.isEmpty()) {
        return new SequentialInteritanceRowDataStore(mainColumn);
      }

      return new MultiplicityRowDataStore(
          mainColumn,
          rconfig.get().getMultiplicities(),
          config.getRowCount(relation.getSubTable()));
    }

    return new RowDataStoreImpl();
  }

  static void registerColumnValueGenerator(final TableDef table, GeneratorConfig config) {
    for (ColumnDef column : table.getColumns()) {

      ColumnConfig colConfig = config.getCommonColumnMap().get(column.getName());

      if (colConfig != null) {
        column.setValueGenerator(colConfig.getSpec());
        continue;
      }

      Optional<ValueGenerator> generatorOpt = config.findValueGenerator(column);
      if (generatorOpt.isPresent()) {
        column.setValueGenerator(generatorOpt.get());
        continue;
      }

      DataTypeValueGenerator generator = valueGenerator(column.getDataType().getName());
      generator.setDataType(column.getDataType());
      column.setValueGenerator(generator);
    }
  }

  static void removeUniqueConstraints(final TableDef table, GeneratorConfig config) {
    TableConfig tblConfig = config.getTableMap().get(table.getName());

    if (tblConfig == null) {
      return;
    }

    for (UniqueConstraintConfig uniqueConstraintConfig : tblConfig.getSkipUniqueCheckConfigs()) {

      List<UniqueConstraintDef> removeConstraints = new ArrayList<>();

      for (UniqueConstraintDef uniqueConstraintDef : table.getUniqueConstraints()) {

        if (uniqueConstraintDef
            .getColumnNames()
            .containsAll(uniqueConstraintConfig.getColumnNames())) {
          removeConstraints.add(uniqueConstraintDef);
          log.debug(
              "Remove unique constraints {} from {}",
              uniqueConstraintConfig.getColumnNames(),
              tblConfig.getName());
        }
      }

      table.getUniqueConstraints().removeAll(removeConstraints);
    }
  }

  static DataTypeValueGenerator valueGenerator(final DataTypeName dataTypeName) {
    switch (dataTypeName) {
      case CHAR:
        return new CharGenerator();
      case DATE:
        return new DateGenerator();
      case DECIMAL:
        return new DecimalGenerator();
      case INTEGER:
        return new IntegerGenerator();
      case TIMESTAMP:
        return new TimestampGenerator();
      case VARCHAR:
        return new VarcharGenerator();
      default:
        return new RandomGenerator();
    }
  }
}
