package io.sitoolkit.rdg.core.domain.schema.jsqlparser;

import io.sitoolkit.rdg.core.domain.schema.ColumnDef;
import io.sitoolkit.rdg.core.domain.schema.ColumnPair;
import io.sitoolkit.rdg.core.domain.schema.RelationDef;
import io.sitoolkit.rdg.core.domain.schema.TableDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.alter.AlterExpression;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import org.apache.commons.lang3.StringUtils;

/** StaticRelationFinder */
@AllArgsConstructor
@Slf4j
public class StaticRelationFinder extends StatementVisitorAdapter {

  private SchemaInfoStore store;

  @Override
  public void visit(CreateTable createTable) {
    log.debug("Visit: {}", createTable);
    store.addTable(JsqlParserConverter.convert(createTable));

    if (createTable.getIndexes() == null) {
      return;
    }

    createTable.getIndexes().stream()
        .filter(ForeignKeyIndex.class::isInstance)
        .map(ForeignKeyIndex.class::cast)
        .forEach(fk -> readAndStoreFk(createTable.getTable(), fk));
  }

  @Override
  public void visit(Alter alter) {
    log.debug("Visit: {}", alter);

    alter.getAlterExpressions().stream()
        .map(AlterExpression::getIndex)
        .filter(Objects::nonNull)
        .filter(ForeignKeyIndex.class::isInstance)
        .map(ForeignKeyIndex.class::cast)
        .forEach(fk -> readAndStoreFk(alter.getTable(), fk));
  }

  public void readAndStoreFk(Table table, ForeignKeyIndex fk) {
    String schemaName = StringUtils.defaultString(table.getSchemaName());
    Optional<TableDef> mainTableOpt = store.findTable(schemaName, fk.getTable().getName());

    if (!mainTableOpt.isPresent()) {
      return;
    }

    String referencedTableName = fk.getTable().getName();
    String readingTableName = table.getName();
    List<ColumnPair> pairs = new ArrayList<>();

    for (int i = 0; i < fk.getColumnsNames().size(); i++) {
      ColumnDef mainColumn =
          store
              .getColumnDef(referencedTableName + "." + fk.getReferencedColumnNames().get(i))
              .orElseThrow();
      ColumnDef subColumn =
          store.getColumnDef(readingTableName + "." + fk.getColumnsNames().get(i)).orElseThrow();

      pairs.add(new ColumnPair(mainColumn, subColumn));
    }

    RelationDef relation = new RelationDef();
    relation.addAllPairs(pairs);

    store.getSchema(schemaName).getRelations().add(relation);
  }
}
