// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'calculation_history.dart';

// **************************************************************************
// IsarCollectionGenerator
// **************************************************************************

// coverage:ignore-file
// ignore_for_file: duplicate_ignore, non_constant_identifier_names, constant_identifier_names, invalid_use_of_protected_member, unnecessary_cast, prefer_const_constructors, lines_longer_than_80_chars, require_trailing_commas, inference_failure_on_function_invocation, unnecessary_parenthesis, unnecessary_raw_strings, unnecessary_null_checks, join_return_with_assignment, prefer_final_locals, avoid_js_rounded_ints, avoid_positional_boolean_parameters, always_specify_types

extension GetCalculationHistoryCollection on Isar {
  IsarCollection<CalculationHistory> get calculationHistorys =>
      this.collection();
}

const CalculationHistorySchema = CollectionSchema(
  name: r'CalculationHistory',
  id: 7453130386061400916,
  properties: {
    r'boxHeight': PropertySchema(
      id: 0,
      name: r'boxHeight',
      type: IsarType.double,
    ),
    r'boxLength': PropertySchema(
      id: 1,
      name: r'boxLength',
      type: IsarType.double,
    ),
    r'boxWeight': PropertySchema(
      id: 2,
      name: r'boxWeight',
      type: IsarType.double,
    ),
    r'boxWidth': PropertySchema(
      id: 3,
      name: r'boxWidth',
      type: IsarType.double,
    ),
    r'boxesPerLayer': PropertySchema(
      id: 4,
      name: r'boxesPerLayer',
      type: IsarType.long,
    ),
    r'createdAt': PropertySchema(
      id: 5,
      name: r'createdAt',
      type: IsarType.dateTime,
    ),
    r'heightLimit': PropertySchema(
      id: 6,
      name: r'heightLimit',
      type: IsarType.double,
    ),
    r'layersCount': PropertySchema(
      id: 7,
      name: r'layersCount',
      type: IsarType.long,
    ),
    r'sku': PropertySchema(
      id: 8,
      name: r'sku',
      type: IsarType.string,
    ),
    r'totalBoxesOnPallet': PropertySchema(
      id: 9,
      name: r'totalBoxesOnPallet',
      type: IsarType.long,
    ),
    r'totalPalletHeight': PropertySchema(
      id: 10,
      name: r'totalPalletHeight',
      type: IsarType.double,
    ),
    r'totalPalletWeight': PropertySchema(
      id: 11,
      name: r'totalPalletWeight',
      type: IsarType.double,
    ),
    r'unitsInBox': PropertySchema(
      id: 12,
      name: r'unitsInBox',
      type: IsarType.long,
    )
  },
  estimateSize: _calculationHistoryEstimateSize,
  serialize: _calculationHistorySerialize,
  deserialize: _calculationHistoryDeserialize,
  deserializeProp: _calculationHistoryDeserializeProp,
  idName: r'id',
  indexes: {},
  links: {},
  embeddedSchemas: {},
  getId: _calculationHistoryGetId,
  getLinks: _calculationHistoryGetLinks,
  attach: _calculationHistoryAttach,
  version: '3.1.0+1',
);

int _calculationHistoryEstimateSize(
  CalculationHistory object,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  var bytesCount = offsets.last;
  {
    final value = object.sku;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  return bytesCount;
}

void _calculationHistorySerialize(
  CalculationHistory object,
  IsarWriter writer,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  writer.writeDouble(offsets[0], object.boxHeight);
  writer.writeDouble(offsets[1], object.boxLength);
  writer.writeDouble(offsets[2], object.boxWeight);
  writer.writeDouble(offsets[3], object.boxWidth);
  writer.writeLong(offsets[4], object.boxesPerLayer);
  writer.writeDateTime(offsets[5], object.createdAt);
  writer.writeDouble(offsets[6], object.heightLimit);
  writer.writeLong(offsets[7], object.layersCount);
  writer.writeString(offsets[8], object.sku);
  writer.writeLong(offsets[9], object.totalBoxesOnPallet);
  writer.writeDouble(offsets[10], object.totalPalletHeight);
  writer.writeDouble(offsets[11], object.totalPalletWeight);
  writer.writeLong(offsets[12], object.unitsInBox);
}

CalculationHistory _calculationHistoryDeserialize(
  Id id,
  IsarReader reader,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  final object = CalculationHistory();
  object.boxHeight = reader.readDoubleOrNull(offsets[0]);
  object.boxLength = reader.readDoubleOrNull(offsets[1]);
  object.boxWeight = reader.readDoubleOrNull(offsets[2]);
  object.boxWidth = reader.readDoubleOrNull(offsets[3]);
  object.boxesPerLayer = reader.readLongOrNull(offsets[4]);
  object.createdAt = reader.readDateTime(offsets[5]);
  object.heightLimit = reader.readDoubleOrNull(offsets[6]);
  object.id = id;
  object.layersCount = reader.readLongOrNull(offsets[7]);
  object.sku = reader.readStringOrNull(offsets[8]);
  object.totalBoxesOnPallet = reader.readLongOrNull(offsets[9]);
  object.totalPalletHeight = reader.readDoubleOrNull(offsets[10]);
  object.totalPalletWeight = reader.readDoubleOrNull(offsets[11]);
  object.unitsInBox = reader.readLongOrNull(offsets[12]);
  return object;
}

P _calculationHistoryDeserializeProp<P>(
  IsarReader reader,
  int propertyId,
  int offset,
  Map<Type, List<int>> allOffsets,
) {
  switch (propertyId) {
    case 0:
      return (reader.readDoubleOrNull(offset)) as P;
    case 1:
      return (reader.readDoubleOrNull(offset)) as P;
    case 2:
      return (reader.readDoubleOrNull(offset)) as P;
    case 3:
      return (reader.readDoubleOrNull(offset)) as P;
    case 4:
      return (reader.readLongOrNull(offset)) as P;
    case 5:
      return (reader.readDateTime(offset)) as P;
    case 6:
      return (reader.readDoubleOrNull(offset)) as P;
    case 7:
      return (reader.readLongOrNull(offset)) as P;
    case 8:
      return (reader.readStringOrNull(offset)) as P;
    case 9:
      return (reader.readLongOrNull(offset)) as P;
    case 10:
      return (reader.readDoubleOrNull(offset)) as P;
    case 11:
      return (reader.readDoubleOrNull(offset)) as P;
    case 12:
      return (reader.readLongOrNull(offset)) as P;
    default:
      throw IsarError('Unknown property with id $propertyId');
  }
}

Id _calculationHistoryGetId(CalculationHistory object) {
  return object.id;
}

List<IsarLinkBase<dynamic>> _calculationHistoryGetLinks(
    CalculationHistory object) {
  return [];
}

void _calculationHistoryAttach(
    IsarCollection<dynamic> col, Id id, CalculationHistory object) {
  object.id = id;
}

extension CalculationHistoryQueryWhereSort
    on QueryBuilder<CalculationHistory, CalculationHistory, QWhere> {
  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhere> anyId() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(const IdWhereClause.any());
    });
  }
}

extension CalculationHistoryQueryWhere
    on QueryBuilder<CalculationHistory, CalculationHistory, QWhereClause> {
  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhereClause>
      idEqualTo(Id id) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IdWhereClause.between(
        lower: id,
        upper: id,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhereClause>
      idNotEqualTo(Id id) {
    return QueryBuilder.apply(this, (query) {
      if (query.whereSort == Sort.asc) {
        return query
            .addWhereClause(
              IdWhereClause.lessThan(upper: id, includeUpper: false),
            )
            .addWhereClause(
              IdWhereClause.greaterThan(lower: id, includeLower: false),
            );
      } else {
        return query
            .addWhereClause(
              IdWhereClause.greaterThan(lower: id, includeLower: false),
            )
            .addWhereClause(
              IdWhereClause.lessThan(upper: id, includeUpper: false),
            );
      }
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhereClause>
      idGreaterThan(Id id, {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.greaterThan(lower: id, includeLower: include),
      );
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhereClause>
      idLessThan(Id id, {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.lessThan(upper: id, includeUpper: include),
      );
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterWhereClause>
      idBetween(
    Id lowerId,
    Id upperId, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IdWhereClause.between(
        lower: lowerId,
        includeLower: includeLower,
        upper: upperId,
        includeUpper: includeUpper,
      ));
    });
  }
}

extension CalculationHistoryQueryFilter
    on QueryBuilder<CalculationHistory, CalculationHistory, QFilterCondition> {
  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxHeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxHeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxHeightBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxHeight',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxLength',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxLength',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxLength',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxLength',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxLength',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxLengthBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxLength',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxWeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxWeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWeightBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxWeight',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxWidth',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxWidth',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxWidth',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxWidth',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxWidth',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxWidthBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxWidth',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxesPerLayer',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxesPerLayer',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxesPerLayer',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxesPerLayer',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxesPerLayer',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      boxesPerLayerBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxesPerLayer',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      createdAtEqualTo(DateTime value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'createdAt',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      createdAtGreaterThan(
    DateTime value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'createdAt',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      createdAtLessThan(
    DateTime value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'createdAt',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      createdAtBetween(
    DateTime lower,
    DateTime upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'createdAt',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'heightLimit',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'heightLimit',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'heightLimit',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'heightLimit',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'heightLimit',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      heightLimitBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'heightLimit',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      idEqualTo(Id value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      idGreaterThan(
    Id value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      idLessThan(
    Id value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      idBetween(
    Id lower,
    Id upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'id',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'layersCount',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'layersCount',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'layersCount',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'layersCount',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'layersCount',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      layersCountBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'layersCount',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'sku',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'sku',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'sku',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'sku',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'sku',
        value: '',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      skuIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'sku',
        value: '',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'totalBoxesOnPallet',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'totalBoxesOnPallet',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'totalBoxesOnPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'totalBoxesOnPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'totalBoxesOnPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalBoxesOnPalletBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'totalBoxesOnPallet',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'totalPalletHeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'totalPalletHeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'totalPalletHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'totalPalletHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'totalPalletHeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletHeightBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'totalPalletHeight',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'totalPalletWeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'totalPalletWeight',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightEqualTo(
    double? value, {
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'totalPalletWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightGreaterThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'totalPalletWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightLessThan(
    double? value, {
    bool include = false,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'totalPalletWeight',
        value: value,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      totalPalletWeightBetween(
    double? lower,
    double? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    double epsilon = Query.epsilon,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'totalPalletWeight',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        epsilon: epsilon,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'unitsInBox',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'unitsInBox',
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'unitsInBox',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'unitsInBox',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'unitsInBox',
        value: value,
      ));
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterFilterCondition>
      unitsInBoxBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'unitsInBox',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }
}

extension CalculationHistoryQueryObject
    on QueryBuilder<CalculationHistory, CalculationHistory, QFilterCondition> {}

extension CalculationHistoryQueryLinks
    on QueryBuilder<CalculationHistory, CalculationHistory, QFilterCondition> {}

extension CalculationHistoryQuerySortBy
    on QueryBuilder<CalculationHistory, CalculationHistory, QSortBy> {
  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxHeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxHeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxHeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxLength() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxLength', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxLengthDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxLength', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxWeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxWidth() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWidth', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxWidthDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWidth', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxesPerLayer() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerLayer', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByBoxesPerLayerDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerLayer', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByHeightLimit() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'heightLimit', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByHeightLimitDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'heightLimit', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByLayersCount() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'layersCount', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByLayersCountDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'layersCount', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortBySku() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortBySkuDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalBoxesOnPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalBoxesOnPallet', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalBoxesOnPalletDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalBoxesOnPallet', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalPalletHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletHeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalPalletHeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletHeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalPalletWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletWeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByTotalPalletWeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletWeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByUnitsInBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsInBox', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      sortByUnitsInBoxDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsInBox', Sort.desc);
    });
  }
}

extension CalculationHistoryQuerySortThenBy
    on QueryBuilder<CalculationHistory, CalculationHistory, QSortThenBy> {
  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxHeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxHeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxHeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxLength() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxLength', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxLengthDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxLength', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxWeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxWidth() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWidth', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxWidthDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxWidth', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxesPerLayer() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerLayer', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByBoxesPerLayerDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerLayer', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByHeightLimit() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'heightLimit', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByHeightLimitDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'heightLimit', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenById() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByLayersCount() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'layersCount', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByLayersCountDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'layersCount', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenBySku() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenBySkuDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalBoxesOnPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalBoxesOnPallet', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalBoxesOnPalletDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalBoxesOnPallet', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalPalletHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletHeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalPalletHeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletHeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalPalletWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletWeight', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByTotalPalletWeightDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalPalletWeight', Sort.desc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByUnitsInBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsInBox', Sort.asc);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QAfterSortBy>
      thenByUnitsInBoxDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsInBox', Sort.desc);
    });
  }
}

extension CalculationHistoryQueryWhereDistinct
    on QueryBuilder<CalculationHistory, CalculationHistory, QDistinct> {
  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByBoxHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxHeight');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByBoxLength() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxLength');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByBoxWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxWeight');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByBoxWidth() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxWidth');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByBoxesPerLayer() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxesPerLayer');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'createdAt');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByHeightLimit() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'heightLimit');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByLayersCount() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'layersCount');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct> distinctBySku(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'sku', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByTotalBoxesOnPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'totalBoxesOnPallet');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByTotalPalletHeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'totalPalletHeight');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByTotalPalletWeight() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'totalPalletWeight');
    });
  }

  QueryBuilder<CalculationHistory, CalculationHistory, QDistinct>
      distinctByUnitsInBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'unitsInBox');
    });
  }
}

extension CalculationHistoryQueryProperty
    on QueryBuilder<CalculationHistory, CalculationHistory, QQueryProperty> {
  QueryBuilder<CalculationHistory, int, QQueryOperations> idProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'id');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      boxHeightProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxHeight');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      boxLengthProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxLength');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      boxWeightProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxWeight');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      boxWidthProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxWidth');
    });
  }

  QueryBuilder<CalculationHistory, int?, QQueryOperations>
      boxesPerLayerProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxesPerLayer');
    });
  }

  QueryBuilder<CalculationHistory, DateTime, QQueryOperations>
      createdAtProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'createdAt');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      heightLimitProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'heightLimit');
    });
  }

  QueryBuilder<CalculationHistory, int?, QQueryOperations>
      layersCountProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'layersCount');
    });
  }

  QueryBuilder<CalculationHistory, String?, QQueryOperations> skuProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'sku');
    });
  }

  QueryBuilder<CalculationHistory, int?, QQueryOperations>
      totalBoxesOnPalletProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'totalBoxesOnPallet');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      totalPalletHeightProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'totalPalletHeight');
    });
  }

  QueryBuilder<CalculationHistory, double?, QQueryOperations>
      totalPalletWeightProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'totalPalletWeight');
    });
  }

  QueryBuilder<CalculationHistory, int?, QQueryOperations>
      unitsInBoxProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'unitsInBox');
    });
  }
}
