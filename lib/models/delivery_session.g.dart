// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'delivery_session.dart';

// **************************************************************************
// IsarCollectionGenerator
// **************************************************************************

// coverage:ignore-file
// ignore_for_file: duplicate_ignore, non_constant_identifier_names, constant_identifier_names, invalid_use_of_protected_member, unnecessary_cast, prefer_const_constructors, lines_longer_than_80_chars, require_trailing_commas, inference_failure_on_function_invocation, unnecessary_parenthesis, unnecessary_raw_strings, unnecessary_null_checks, join_return_with_assignment, prefer_final_locals, avoid_js_rounded_ints, avoid_positional_boolean_parameters, always_specify_types

extension GetDeliverySessionCollection on Isar {
  IsarCollection<DeliverySession> get deliverySessions => this.collection();
}

const DeliverySessionSchema = CollectionSchema(
  name: r'DeliverySession',
  id: -1467499352859752419,
  properties: {
    r'createdAt': PropertySchema(
      id: 0,
      name: r'createdAt',
      type: IsarType.dateTime,
    ),
    r'deliveryType': PropertySchema(
      id: 1,
      name: r'deliveryType',
      type: IsarType.string,
    ),
    r'endTime': PropertySchema(
      id: 2,
      name: r'endTime',
      type: IsarType.dateTime,
    ),
    r'isArchived': PropertySchema(
      id: 3,
      name: r'isArchived',
      type: IsarType.bool,
    ),
    r'isCompleted': PropertySchema(
      id: 4,
      name: r'isCompleted',
      type: IsarType.bool,
    ),
    r'name': PropertySchema(
      id: 5,
      name: r'name',
      type: IsarType.string,
    ),
    r'rampNumber': PropertySchema(
      id: 6,
      name: r'rampNumber',
      type: IsarType.long,
    ),
    r'startTime': PropertySchema(
      id: 7,
      name: r'startTime',
      type: IsarType.dateTime,
    ),
    r'totalCalculatedPallets': PropertySchema(
      id: 8,
      name: r'totalCalculatedPallets',
      type: IsarType.long,
    )
  },
  estimateSize: _deliverySessionEstimateSize,
  serialize: _deliverySessionSerialize,
  deserialize: _deliverySessionDeserialize,
  deserializeProp: _deliverySessionDeserializeProp,
  idName: r'id',
  indexes: {},
  links: {},
  embeddedSchemas: {},
  getId: _deliverySessionGetId,
  getLinks: _deliverySessionGetLinks,
  attach: _deliverySessionAttach,
  version: '3.1.0+1',
);

int _deliverySessionEstimateSize(
  DeliverySession object,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  var bytesCount = offsets.last;
  {
    final value = object.deliveryType;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  {
    final value = object.name;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  return bytesCount;
}

void _deliverySessionSerialize(
  DeliverySession object,
  IsarWriter writer,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  writer.writeDateTime(offsets[0], object.createdAt);
  writer.writeString(offsets[1], object.deliveryType);
  writer.writeDateTime(offsets[2], object.endTime);
  writer.writeBool(offsets[3], object.isArchived);
  writer.writeBool(offsets[4], object.isCompleted);
  writer.writeString(offsets[5], object.name);
  writer.writeLong(offsets[6], object.rampNumber);
  writer.writeDateTime(offsets[7], object.startTime);
  writer.writeLong(offsets[8], object.totalCalculatedPallets);
}

DeliverySession _deliverySessionDeserialize(
  Id id,
  IsarReader reader,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  final object = DeliverySession();
  object.createdAt = reader.readDateTime(offsets[0]);
  object.deliveryType = reader.readStringOrNull(offsets[1]);
  object.endTime = reader.readDateTimeOrNull(offsets[2]);
  object.id = id;
  object.isArchived = reader.readBool(offsets[3]);
  object.isCompleted = reader.readBool(offsets[4]);
  object.name = reader.readStringOrNull(offsets[5]);
  object.rampNumber = reader.readLongOrNull(offsets[6]);
  object.startTime = reader.readDateTimeOrNull(offsets[7]);
  object.totalCalculatedPallets = reader.readLong(offsets[8]);
  return object;
}

P _deliverySessionDeserializeProp<P>(
  IsarReader reader,
  int propertyId,
  int offset,
  Map<Type, List<int>> allOffsets,
) {
  switch (propertyId) {
    case 0:
      return (reader.readDateTime(offset)) as P;
    case 1:
      return (reader.readStringOrNull(offset)) as P;
    case 2:
      return (reader.readDateTimeOrNull(offset)) as P;
    case 3:
      return (reader.readBool(offset)) as P;
    case 4:
      return (reader.readBool(offset)) as P;
    case 5:
      return (reader.readStringOrNull(offset)) as P;
    case 6:
      return (reader.readLongOrNull(offset)) as P;
    case 7:
      return (reader.readDateTimeOrNull(offset)) as P;
    case 8:
      return (reader.readLong(offset)) as P;
    default:
      throw IsarError('Unknown property with id $propertyId');
  }
}

Id _deliverySessionGetId(DeliverySession object) {
  return object.id;
}

List<IsarLinkBase<dynamic>> _deliverySessionGetLinks(DeliverySession object) {
  return [];
}

void _deliverySessionAttach(
    IsarCollection<dynamic> col, Id id, DeliverySession object) {
  object.id = id;
}

extension DeliverySessionQueryWhereSort
    on QueryBuilder<DeliverySession, DeliverySession, QWhere> {
  QueryBuilder<DeliverySession, DeliverySession, QAfterWhere> anyId() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(const IdWhereClause.any());
    });
  }
}

extension DeliverySessionQueryWhere
    on QueryBuilder<DeliverySession, DeliverySession, QWhereClause> {
  QueryBuilder<DeliverySession, DeliverySession, QAfterWhereClause> idEqualTo(
      Id id) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IdWhereClause.between(
        lower: id,
        upper: id,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterWhereClause>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterWhereClause>
      idGreaterThan(Id id, {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.greaterThan(lower: id, includeLower: include),
      );
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterWhereClause> idLessThan(
      Id id,
      {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.lessThan(upper: id, includeUpper: include),
      );
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterWhereClause> idBetween(
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

extension DeliverySessionQueryFilter
    on QueryBuilder<DeliverySession, DeliverySession, QFilterCondition> {
  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      createdAtEqualTo(DateTime value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'createdAt',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'deliveryType',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'deliveryType',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'deliveryType',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'deliveryType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'deliveryType',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'deliveryType',
        value: '',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      deliveryTypeIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'deliveryType',
        value: '',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'endTime',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'endTime',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeEqualTo(DateTime? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'endTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeGreaterThan(
    DateTime? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'endTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeLessThan(
    DateTime? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'endTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      endTimeBetween(
    DateTime? lower,
    DateTime? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'endTime',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      idEqualTo(Id value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
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

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      isArchivedEqualTo(bool value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'isArchived',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      isCompletedEqualTo(bool value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'isCompleted',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'name',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'name',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'name',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'name',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'name',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'name',
        value: '',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      nameIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'name',
        value: '',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'rampNumber',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'rampNumber',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'rampNumber',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'rampNumber',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'rampNumber',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      rampNumberBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'rampNumber',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'startTime',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'startTime',
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeEqualTo(DateTime? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'startTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeGreaterThan(
    DateTime? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'startTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeLessThan(
    DateTime? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'startTime',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      startTimeBetween(
    DateTime? lower,
    DateTime? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'startTime',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      totalCalculatedPalletsEqualTo(int value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'totalCalculatedPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      totalCalculatedPalletsGreaterThan(
    int value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'totalCalculatedPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      totalCalculatedPalletsLessThan(
    int value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'totalCalculatedPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterFilterCondition>
      totalCalculatedPalletsBetween(
    int lower,
    int upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'totalCalculatedPallets',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }
}

extension DeliverySessionQueryObject
    on QueryBuilder<DeliverySession, DeliverySession, QFilterCondition> {}

extension DeliverySessionQueryLinks
    on QueryBuilder<DeliverySession, DeliverySession, QFilterCondition> {}

extension DeliverySessionQuerySortBy
    on QueryBuilder<DeliverySession, DeliverySession, QSortBy> {
  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByDeliveryType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'deliveryType', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByDeliveryTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'deliveryType', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> sortByEndTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'endTime', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByEndTimeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'endTime', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByIsArchived() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isArchived', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByIsArchivedDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isArchived', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByIsCompleted() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isCompleted', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByIsCompletedDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isCompleted', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> sortByName() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'name', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByNameDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'name', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByRampNumber() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'rampNumber', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByRampNumberDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'rampNumber', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByStartTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'startTime', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByStartTimeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'startTime', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByTotalCalculatedPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalCalculatedPallets', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      sortByTotalCalculatedPalletsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalCalculatedPallets', Sort.desc);
    });
  }
}

extension DeliverySessionQuerySortThenBy
    on QueryBuilder<DeliverySession, DeliverySession, QSortThenBy> {
  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByDeliveryType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'deliveryType', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByDeliveryTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'deliveryType', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> thenByEndTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'endTime', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByEndTimeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'endTime', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> thenById() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> thenByIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByIsArchived() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isArchived', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByIsArchivedDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isArchived', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByIsCompleted() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isCompleted', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByIsCompletedDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isCompleted', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy> thenByName() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'name', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByNameDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'name', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByRampNumber() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'rampNumber', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByRampNumberDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'rampNumber', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByStartTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'startTime', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByStartTimeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'startTime', Sort.desc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByTotalCalculatedPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalCalculatedPallets', Sort.asc);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QAfterSortBy>
      thenByTotalCalculatedPalletsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalCalculatedPallets', Sort.desc);
    });
  }
}

extension DeliverySessionQueryWhereDistinct
    on QueryBuilder<DeliverySession, DeliverySession, QDistinct> {
  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'createdAt');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByDeliveryType({bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'deliveryType', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByEndTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'endTime');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByIsArchived() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'isArchived');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByIsCompleted() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'isCompleted');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct> distinctByName(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'name', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByRampNumber() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'rampNumber');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByStartTime() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'startTime');
    });
  }

  QueryBuilder<DeliverySession, DeliverySession, QDistinct>
      distinctByTotalCalculatedPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'totalCalculatedPallets');
    });
  }
}

extension DeliverySessionQueryProperty
    on QueryBuilder<DeliverySession, DeliverySession, QQueryProperty> {
  QueryBuilder<DeliverySession, int, QQueryOperations> idProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'id');
    });
  }

  QueryBuilder<DeliverySession, DateTime, QQueryOperations>
      createdAtProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'createdAt');
    });
  }

  QueryBuilder<DeliverySession, String?, QQueryOperations>
      deliveryTypeProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'deliveryType');
    });
  }

  QueryBuilder<DeliverySession, DateTime?, QQueryOperations> endTimeProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'endTime');
    });
  }

  QueryBuilder<DeliverySession, bool, QQueryOperations> isArchivedProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'isArchived');
    });
  }

  QueryBuilder<DeliverySession, bool, QQueryOperations> isCompletedProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'isCompleted');
    });
  }

  QueryBuilder<DeliverySession, String?, QQueryOperations> nameProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'name');
    });
  }

  QueryBuilder<DeliverySession, int?, QQueryOperations> rampNumberProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'rampNumber');
    });
  }

  QueryBuilder<DeliverySession, DateTime?, QQueryOperations>
      startTimeProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'startTime');
    });
  }

  QueryBuilder<DeliverySession, int, QQueryOperations>
      totalCalculatedPalletsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'totalCalculatedPallets');
    });
  }
}

// coverage:ignore-file
// ignore_for_file: duplicate_ignore, non_constant_identifier_names, constant_identifier_names, invalid_use_of_protected_member, unnecessary_cast, prefer_const_constructors, lines_longer_than_80_chars, require_trailing_commas, inference_failure_on_function_invocation, unnecessary_parenthesis, unnecessary_raw_strings, unnecessary_null_checks, join_return_with_assignment, prefer_final_locals, avoid_js_rounded_ints, avoid_positional_boolean_parameters, always_specify_types

extension GetDocumentPhotoCollection on Isar {
  IsarCollection<DocumentPhoto> get documentPhotos => this.collection();
}

const DocumentPhotoSchema = CollectionSchema(
  name: r'DocumentPhoto',
  id: -7678038977134892338,
  properties: {
    r'addedAt': PropertySchema(
      id: 0,
      name: r'addedAt',
      type: IsarType.dateTime,
    ),
    r'comment': PropertySchema(
      id: 1,
      name: r'comment',
      type: IsarType.string,
    ),
    r'imagePath': PropertySchema(
      id: 2,
      name: r'imagePath',
      type: IsarType.string,
    ),
    r'isDone': PropertySchema(
      id: 3,
      name: r'isDone',
      type: IsarType.bool,
    ),
    r'photoType': PropertySchema(
      id: 4,
      name: r'photoType',
      type: IsarType.string,
    ),
    r'recognizedText': PropertySchema(
      id: 5,
      name: r'recognizedText',
      type: IsarType.stringList,
    ),
    r'sessionId': PropertySchema(
      id: 6,
      name: r'sessionId',
      type: IsarType.long,
    )
  },
  estimateSize: _documentPhotoEstimateSize,
  serialize: _documentPhotoSerialize,
  deserialize: _documentPhotoDeserialize,
  deserializeProp: _documentPhotoDeserializeProp,
  idName: r'id',
  indexes: {
    r'recognizedText': IndexSchema(
      id: 2676081159980004107,
      name: r'recognizedText',
      unique: false,
      replace: false,
      properties: [
        IndexPropertySchema(
          name: r'recognizedText',
          type: IndexType.value,
          caseSensitive: true,
        )
      ],
    )
  },
  links: {},
  embeddedSchemas: {},
  getId: _documentPhotoGetId,
  getLinks: _documentPhotoGetLinks,
  attach: _documentPhotoAttach,
  version: '3.1.0+1',
);

int _documentPhotoEstimateSize(
  DocumentPhoto object,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  var bytesCount = offsets.last;
  {
    final value = object.comment;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  {
    final value = object.imagePath;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  {
    final value = object.photoType;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  {
    final list = object.recognizedText;
    if (list != null) {
      bytesCount += 3 + list.length * 3;
      {
        for (var i = 0; i < list.length; i++) {
          final value = list[i];
          bytesCount += value.length * 3;
        }
      }
    }
  }
  return bytesCount;
}

void _documentPhotoSerialize(
  DocumentPhoto object,
  IsarWriter writer,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  writer.writeDateTime(offsets[0], object.addedAt);
  writer.writeString(offsets[1], object.comment);
  writer.writeString(offsets[2], object.imagePath);
  writer.writeBool(offsets[3], object.isDone);
  writer.writeString(offsets[4], object.photoType);
  writer.writeStringList(offsets[5], object.recognizedText);
  writer.writeLong(offsets[6], object.sessionId);
}

DocumentPhoto _documentPhotoDeserialize(
  Id id,
  IsarReader reader,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  final object = DocumentPhoto();
  object.addedAt = reader.readDateTime(offsets[0]);
  object.comment = reader.readStringOrNull(offsets[1]);
  object.id = id;
  object.imagePath = reader.readStringOrNull(offsets[2]);
  object.isDone = reader.readBool(offsets[3]);
  object.photoType = reader.readStringOrNull(offsets[4]);
  object.recognizedText = reader.readStringList(offsets[5]);
  object.sessionId = reader.readLongOrNull(offsets[6]);
  return object;
}

P _documentPhotoDeserializeProp<P>(
  IsarReader reader,
  int propertyId,
  int offset,
  Map<Type, List<int>> allOffsets,
) {
  switch (propertyId) {
    case 0:
      return (reader.readDateTime(offset)) as P;
    case 1:
      return (reader.readStringOrNull(offset)) as P;
    case 2:
      return (reader.readStringOrNull(offset)) as P;
    case 3:
      return (reader.readBool(offset)) as P;
    case 4:
      return (reader.readStringOrNull(offset)) as P;
    case 5:
      return (reader.readStringList(offset)) as P;
    case 6:
      return (reader.readLongOrNull(offset)) as P;
    default:
      throw IsarError('Unknown property with id $propertyId');
  }
}

Id _documentPhotoGetId(DocumentPhoto object) {
  return object.id;
}

List<IsarLinkBase<dynamic>> _documentPhotoGetLinks(DocumentPhoto object) {
  return [];
}

void _documentPhotoAttach(
    IsarCollection<dynamic> col, Id id, DocumentPhoto object) {
  object.id = id;
}

extension DocumentPhotoQueryWhereSort
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QWhere> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhere> anyId() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(const IdWhereClause.any());
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhere>
      anyRecognizedTextElement() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        const IndexWhereClause.any(indexName: r'recognizedText'),
      );
    });
  }
}

extension DocumentPhotoQueryWhere
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QWhereClause> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause> idEqualTo(
      Id id) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IdWhereClause.between(
        lower: id,
        upper: id,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause> idNotEqualTo(
      Id id) {
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

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause> idGreaterThan(
      Id id,
      {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.greaterThan(lower: id, includeLower: include),
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause> idLessThan(
      Id id,
      {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.lessThan(upper: id, includeUpper: include),
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause> idBetween(
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

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementEqualTo(String recognizedTextElement) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.equalTo(
        indexName: r'recognizedText',
        value: [recognizedTextElement],
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementNotEqualTo(String recognizedTextElement) {
    return QueryBuilder.apply(this, (query) {
      if (query.whereSort == Sort.asc) {
        return query
            .addWhereClause(IndexWhereClause.between(
              indexName: r'recognizedText',
              lower: [],
              upper: [recognizedTextElement],
              includeUpper: false,
            ))
            .addWhereClause(IndexWhereClause.between(
              indexName: r'recognizedText',
              lower: [recognizedTextElement],
              includeLower: false,
              upper: [],
            ));
      } else {
        return query
            .addWhereClause(IndexWhereClause.between(
              indexName: r'recognizedText',
              lower: [recognizedTextElement],
              includeLower: false,
              upper: [],
            ))
            .addWhereClause(IndexWhereClause.between(
              indexName: r'recognizedText',
              lower: [],
              upper: [recognizedTextElement],
              includeUpper: false,
            ));
      }
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementGreaterThan(
    String recognizedTextElement, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.between(
        indexName: r'recognizedText',
        lower: [recognizedTextElement],
        includeLower: include,
        upper: [],
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementLessThan(
    String recognizedTextElement, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.between(
        indexName: r'recognizedText',
        lower: [],
        upper: [recognizedTextElement],
        includeUpper: include,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementBetween(
    String lowerRecognizedTextElement,
    String upperRecognizedTextElement, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.between(
        indexName: r'recognizedText',
        lower: [lowerRecognizedTextElement],
        includeLower: includeLower,
        upper: [upperRecognizedTextElement],
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementStartsWith(String RecognizedTextElementPrefix) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.between(
        indexName: r'recognizedText',
        lower: [RecognizedTextElementPrefix],
        upper: ['$RecognizedTextElementPrefix\u{FFFFF}'],
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IndexWhereClause.equalTo(
        indexName: r'recognizedText',
        value: [''],
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterWhereClause>
      recognizedTextElementIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      if (query.whereSort == Sort.asc) {
        return query
            .addWhereClause(IndexWhereClause.lessThan(
              indexName: r'recognizedText',
              upper: [''],
            ))
            .addWhereClause(IndexWhereClause.greaterThan(
              indexName: r'recognizedText',
              lower: [''],
            ));
      } else {
        return query
            .addWhereClause(IndexWhereClause.greaterThan(
              indexName: r'recognizedText',
              lower: [''],
            ))
            .addWhereClause(IndexWhereClause.lessThan(
              indexName: r'recognizedText',
              upper: [''],
            ));
      }
    });
  }
}

extension DocumentPhotoQueryFilter
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QFilterCondition> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      addedAtEqualTo(DateTime value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'addedAt',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      addedAtGreaterThan(
    DateTime value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'addedAt',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      addedAtLessThan(
    DateTime value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'addedAt',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      addedAtBetween(
    DateTime lower,
    DateTime upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'addedAt',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'comment',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'comment',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'comment',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'comment',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'comment',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'comment',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      commentIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'comment',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition> idEqualTo(
      Id value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
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

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition> idLessThan(
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

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition> idBetween(
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

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'imagePath',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'imagePath',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'imagePath',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'imagePath',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'imagePath',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'imagePath',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      imagePathIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'imagePath',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      isDoneEqualTo(bool value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'isDone',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'photoType',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'photoType',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'photoType',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'photoType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'photoType',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'photoType',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      photoTypeIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'photoType',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'recognizedText',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'recognizedText',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementEqualTo(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementGreaterThan(
    String value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementLessThan(
    String value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementBetween(
    String lower,
    String upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'recognizedText',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'recognizedText',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementMatches(String pattern,
          {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'recognizedText',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'recognizedText',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextElementIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'recognizedText',
        value: '',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextLengthEqualTo(int length) {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        length,
        true,
        length,
        true,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        0,
        true,
        0,
        true,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        0,
        false,
        999999,
        true,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextLengthLessThan(
    int length, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        0,
        true,
        length,
        include,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextLengthGreaterThan(
    int length, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        length,
        include,
        999999,
        true,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      recognizedTextLengthBetween(
    int lower,
    int upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.listLength(
        r'recognizedText',
        lower,
        includeLower,
        upper,
        includeUpper,
      );
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'sessionId',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'sessionId',
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterFilterCondition>
      sessionIdBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'sessionId',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }
}

extension DocumentPhotoQueryObject
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QFilterCondition> {}

extension DocumentPhotoQueryLinks
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QFilterCondition> {}

extension DocumentPhotoQuerySortBy
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QSortBy> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByAddedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'addedAt', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByAddedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'addedAt', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByComment() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'comment', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByCommentDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'comment', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByImagePath() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'imagePath', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      sortByImagePathDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'imagePath', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByIsDone() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isDone', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByIsDoneDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isDone', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortByPhotoType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoType', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      sortByPhotoTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoType', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> sortBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      sortBySessionIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.desc);
    });
  }
}

extension DocumentPhotoQuerySortThenBy
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QSortThenBy> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByAddedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'addedAt', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByAddedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'addedAt', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByComment() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'comment', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByCommentDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'comment', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenById() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByImagePath() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'imagePath', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      thenByImagePathDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'imagePath', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByIsDone() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isDone', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByIsDoneDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isDone', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenByPhotoType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoType', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      thenByPhotoTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoType', Sort.desc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy> thenBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.asc);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QAfterSortBy>
      thenBySessionIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.desc);
    });
  }
}

extension DocumentPhotoQueryWhereDistinct
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> {
  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctByAddedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'addedAt');
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctByComment(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'comment', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctByImagePath(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'imagePath', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctByIsDone() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'isDone');
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctByPhotoType(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'photoType', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct>
      distinctByRecognizedText() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'recognizedText');
    });
  }

  QueryBuilder<DocumentPhoto, DocumentPhoto, QDistinct> distinctBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'sessionId');
    });
  }
}

extension DocumentPhotoQueryProperty
    on QueryBuilder<DocumentPhoto, DocumentPhoto, QQueryProperty> {
  QueryBuilder<DocumentPhoto, int, QQueryOperations> idProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'id');
    });
  }

  QueryBuilder<DocumentPhoto, DateTime, QQueryOperations> addedAtProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'addedAt');
    });
  }

  QueryBuilder<DocumentPhoto, String?, QQueryOperations> commentProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'comment');
    });
  }

  QueryBuilder<DocumentPhoto, String?, QQueryOperations> imagePathProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'imagePath');
    });
  }

  QueryBuilder<DocumentPhoto, bool, QQueryOperations> isDoneProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'isDone');
    });
  }

  QueryBuilder<DocumentPhoto, String?, QQueryOperations> photoTypeProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'photoType');
    });
  }

  QueryBuilder<DocumentPhoto, List<String>?, QQueryOperations>
      recognizedTextProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'recognizedText');
    });
  }

  QueryBuilder<DocumentPhoto, int?, QQueryOperations> sessionIdProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'sessionId');
    });
  }
}

// coverage:ignore-file
// ignore_for_file: duplicate_ignore, non_constant_identifier_names, constant_identifier_names, invalid_use_of_protected_member, unnecessary_cast, prefer_const_constructors, lines_longer_than_80_chars, require_trailing_commas, inference_failure_on_function_invocation, unnecessary_parenthesis, unnecessary_raw_strings, unnecessary_null_checks, join_return_with_assignment, prefer_final_locals, avoid_js_rounded_ints, avoid_positional_boolean_parameters, always_specify_types

extension GetPalletCalculationCollection on Isar {
  IsarCollection<PalletCalculation> get palletCalculations => this.collection();
}

const PalletCalculationSchema = CollectionSchema(
  name: r'PalletCalculation',
  id: -1395562312114762432,
  properties: {
    r'acceptedUnits': PropertySchema(
      id: 0,
      name: r'acceptedUnits',
      type: IsarType.long,
    ),
    r'boxesPerPallet': PropertySchema(
      id: 1,
      name: r'boxesPerPallet',
      type: IsarType.long,
    ),
    r'carrierType': PropertySchema(
      id: 2,
      name: r'carrierType',
      type: IsarType.string,
    ),
    r'createdAt': PropertySchema(
      id: 3,
      name: r'createdAt',
      type: IsarType.dateTime,
    ),
    r'expectedUnits': PropertySchema(
      id: 4,
      name: r'expectedUnits',
      type: IsarType.long,
    ),
    r'fullPallets': PropertySchema(
      id: 5,
      name: r'fullPallets',
      type: IsarType.long,
    ),
    r'isManual': PropertySchema(
      id: 6,
      name: r'isManual',
      type: IsarType.bool,
    ),
    r'looseUnits': PropertySchema(
      id: 7,
      name: r'looseUnits',
      type: IsarType.long,
    ),
    r'photoId': PropertySchema(
      id: 8,
      name: r'photoId',
      type: IsarType.long,
    ),
    r'remainderBoxes': PropertySchema(
      id: 9,
      name: r'remainderBoxes',
      type: IsarType.long,
    ),
    r'sessionId': PropertySchema(
      id: 10,
      name: r'sessionId',
      type: IsarType.long,
    ),
    r'sku': PropertySchema(
      id: 11,
      name: r'sku',
      type: IsarType.string,
    ),
    r'totalUnits': PropertySchema(
      id: 12,
      name: r'totalUnits',
      type: IsarType.long,
    ),
    r'unitsPerBox': PropertySchema(
      id: 13,
      name: r'unitsPerBox',
      type: IsarType.long,
    )
  },
  estimateSize: _palletCalculationEstimateSize,
  serialize: _palletCalculationSerialize,
  deserialize: _palletCalculationDeserialize,
  deserializeProp: _palletCalculationDeserializeProp,
  idName: r'id',
  indexes: {},
  links: {},
  embeddedSchemas: {},
  getId: _palletCalculationGetId,
  getLinks: _palletCalculationGetLinks,
  attach: _palletCalculationAttach,
  version: '3.1.0+1',
);

int _palletCalculationEstimateSize(
  PalletCalculation object,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  var bytesCount = offsets.last;
  {
    final value = object.carrierType;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  {
    final value = object.sku;
    if (value != null) {
      bytesCount += 3 + value.length * 3;
    }
  }
  return bytesCount;
}

void _palletCalculationSerialize(
  PalletCalculation object,
  IsarWriter writer,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  writer.writeLong(offsets[0], object.acceptedUnits);
  writer.writeLong(offsets[1], object.boxesPerPallet);
  writer.writeString(offsets[2], object.carrierType);
  writer.writeDateTime(offsets[3], object.createdAt);
  writer.writeLong(offsets[4], object.expectedUnits);
  writer.writeLong(offsets[5], object.fullPallets);
  writer.writeBool(offsets[6], object.isManual);
  writer.writeLong(offsets[7], object.looseUnits);
  writer.writeLong(offsets[8], object.photoId);
  writer.writeLong(offsets[9], object.remainderBoxes);
  writer.writeLong(offsets[10], object.sessionId);
  writer.writeString(offsets[11], object.sku);
  writer.writeLong(offsets[12], object.totalUnits);
  writer.writeLong(offsets[13], object.unitsPerBox);
}

PalletCalculation _palletCalculationDeserialize(
  Id id,
  IsarReader reader,
  List<int> offsets,
  Map<Type, List<int>> allOffsets,
) {
  final object = PalletCalculation();
  object.acceptedUnits = reader.readLongOrNull(offsets[0]);
  object.boxesPerPallet = reader.readLongOrNull(offsets[1]);
  object.carrierType = reader.readStringOrNull(offsets[2]);
  object.createdAt = reader.readDateTime(offsets[3]);
  object.expectedUnits = reader.readLongOrNull(offsets[4]);
  object.fullPallets = reader.readLongOrNull(offsets[5]);
  object.id = id;
  object.isManual = reader.readBool(offsets[6]);
  object.looseUnits = reader.readLongOrNull(offsets[7]);
  object.photoId = reader.readLongOrNull(offsets[8]);
  object.remainderBoxes = reader.readLongOrNull(offsets[9]);
  object.sessionId = reader.readLongOrNull(offsets[10]);
  object.sku = reader.readStringOrNull(offsets[11]);
  object.totalUnits = reader.readLongOrNull(offsets[12]);
  object.unitsPerBox = reader.readLongOrNull(offsets[13]);
  return object;
}

P _palletCalculationDeserializeProp<P>(
  IsarReader reader,
  int propertyId,
  int offset,
  Map<Type, List<int>> allOffsets,
) {
  switch (propertyId) {
    case 0:
      return (reader.readLongOrNull(offset)) as P;
    case 1:
      return (reader.readLongOrNull(offset)) as P;
    case 2:
      return (reader.readStringOrNull(offset)) as P;
    case 3:
      return (reader.readDateTime(offset)) as P;
    case 4:
      return (reader.readLongOrNull(offset)) as P;
    case 5:
      return (reader.readLongOrNull(offset)) as P;
    case 6:
      return (reader.readBool(offset)) as P;
    case 7:
      return (reader.readLongOrNull(offset)) as P;
    case 8:
      return (reader.readLongOrNull(offset)) as P;
    case 9:
      return (reader.readLongOrNull(offset)) as P;
    case 10:
      return (reader.readLongOrNull(offset)) as P;
    case 11:
      return (reader.readStringOrNull(offset)) as P;
    case 12:
      return (reader.readLongOrNull(offset)) as P;
    case 13:
      return (reader.readLongOrNull(offset)) as P;
    default:
      throw IsarError('Unknown property with id $propertyId');
  }
}

Id _palletCalculationGetId(PalletCalculation object) {
  return object.id;
}

List<IsarLinkBase<dynamic>> _palletCalculationGetLinks(
    PalletCalculation object) {
  return [];
}

void _palletCalculationAttach(
    IsarCollection<dynamic> col, Id id, PalletCalculation object) {
  object.id = id;
}

extension PalletCalculationQueryWhereSort
    on QueryBuilder<PalletCalculation, PalletCalculation, QWhere> {
  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhere> anyId() {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(const IdWhereClause.any());
    });
  }
}

extension PalletCalculationQueryWhere
    on QueryBuilder<PalletCalculation, PalletCalculation, QWhereClause> {
  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhereClause>
      idEqualTo(Id id) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(IdWhereClause.between(
        lower: id,
        upper: id,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhereClause>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhereClause>
      idGreaterThan(Id id, {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.greaterThan(lower: id, includeLower: include),
      );
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhereClause>
      idLessThan(Id id, {bool include = false}) {
    return QueryBuilder.apply(this, (query) {
      return query.addWhereClause(
        IdWhereClause.lessThan(upper: id, includeUpper: include),
      );
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterWhereClause>
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

extension PalletCalculationQueryFilter
    on QueryBuilder<PalletCalculation, PalletCalculation, QFilterCondition> {
  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'acceptedUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'acceptedUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'acceptedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'acceptedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'acceptedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      acceptedUnitsBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'acceptedUnits',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'boxesPerPallet',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'boxesPerPallet',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'boxesPerPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'boxesPerPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'boxesPerPallet',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      boxesPerPalletBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'boxesPerPallet',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'carrierType',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'carrierType',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeEqualTo(
    String? value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeGreaterThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeLessThan(
    String? value, {
    bool include = false,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeBetween(
    String? lower,
    String? upper, {
    bool includeLower = true,
    bool includeUpper = true,
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'carrierType',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeStartsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.startsWith(
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeEndsWith(
    String value, {
    bool caseSensitive = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.endsWith(
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'carrierType',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'carrierType',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'carrierType',
        value: '',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      carrierTypeIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'carrierType',
        value: '',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      createdAtEqualTo(DateTime value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'createdAt',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'expectedUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'expectedUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'expectedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'expectedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'expectedUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      expectedUnitsBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'expectedUnits',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'fullPallets',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'fullPallets',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'fullPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'fullPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'fullPallets',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      fullPalletsBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'fullPallets',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      idEqualTo(Id value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'id',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      isManualEqualTo(bool value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'isManual',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'looseUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'looseUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'looseUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'looseUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'looseUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      looseUnitsBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'looseUnits',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'photoId',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'photoId',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'photoId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'photoId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'photoId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      photoIdBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'photoId',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'remainderBoxes',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'remainderBoxes',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'remainderBoxes',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'remainderBoxes',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'remainderBoxes',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      remainderBoxesBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'remainderBoxes',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'sessionId',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'sessionId',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'sessionId',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      sessionIdBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'sessionId',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'sku',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'sku',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
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

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuContains(String value, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.contains(
        property: r'sku',
        value: value,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuMatches(String pattern, {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.matches(
        property: r'sku',
        wildcard: pattern,
        caseSensitive: caseSensitive,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuIsEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'sku',
        value: '',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      skuIsNotEmpty() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        property: r'sku',
        value: '',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'totalUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'totalUnits',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'totalUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'totalUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'totalUnits',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      totalUnitsBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'totalUnits',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxIsNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNull(
        property: r'unitsPerBox',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxIsNotNull() {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(const FilterCondition.isNotNull(
        property: r'unitsPerBox',
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxEqualTo(int? value) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.equalTo(
        property: r'unitsPerBox',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxGreaterThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.greaterThan(
        include: include,
        property: r'unitsPerBox',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxLessThan(
    int? value, {
    bool include = false,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.lessThan(
        include: include,
        property: r'unitsPerBox',
        value: value,
      ));
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterFilterCondition>
      unitsPerBoxBetween(
    int? lower,
    int? upper, {
    bool includeLower = true,
    bool includeUpper = true,
  }) {
    return QueryBuilder.apply(this, (query) {
      return query.addFilterCondition(FilterCondition.between(
        property: r'unitsPerBox',
        lower: lower,
        includeLower: includeLower,
        upper: upper,
        includeUpper: includeUpper,
      ));
    });
  }
}

extension PalletCalculationQueryObject
    on QueryBuilder<PalletCalculation, PalletCalculation, QFilterCondition> {}

extension PalletCalculationQueryLinks
    on QueryBuilder<PalletCalculation, PalletCalculation, QFilterCondition> {}

extension PalletCalculationQuerySortBy
    on QueryBuilder<PalletCalculation, PalletCalculation, QSortBy> {
  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByAcceptedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'acceptedUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByAcceptedUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'acceptedUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByBoxesPerPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerPallet', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByBoxesPerPalletDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerPallet', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByCarrierType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'carrierType', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByCarrierTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'carrierType', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByExpectedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'expectedUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByExpectedUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'expectedUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByFullPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'fullPallets', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByFullPalletsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'fullPallets', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByIsManual() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isManual', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByIsManualDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isManual', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByLooseUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'looseUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByLooseUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'looseUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByPhotoId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoId', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByPhotoIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoId', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByRemainderBoxes() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'remainderBoxes', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByRemainderBoxesDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'remainderBoxes', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortBySessionIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy> sortBySku() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortBySkuDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByTotalUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByTotalUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByUnitsPerBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsPerBox', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      sortByUnitsPerBoxDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsPerBox', Sort.desc);
    });
  }
}

extension PalletCalculationQuerySortThenBy
    on QueryBuilder<PalletCalculation, PalletCalculation, QSortThenBy> {
  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByAcceptedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'acceptedUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByAcceptedUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'acceptedUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByBoxesPerPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerPallet', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByBoxesPerPalletDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'boxesPerPallet', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByCarrierType() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'carrierType', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByCarrierTypeDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'carrierType', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByCreatedAtDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'createdAt', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByExpectedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'expectedUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByExpectedUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'expectedUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByFullPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'fullPallets', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByFullPalletsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'fullPallets', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy> thenById() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'id', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByIsManual() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isManual', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByIsManualDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'isManual', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByLooseUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'looseUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByLooseUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'looseUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByPhotoId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoId', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByPhotoIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'photoId', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByRemainderBoxes() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'remainderBoxes', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByRemainderBoxesDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'remainderBoxes', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenBySessionIdDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sessionId', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy> thenBySku() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenBySkuDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'sku', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByTotalUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalUnits', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByTotalUnitsDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'totalUnits', Sort.desc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByUnitsPerBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsPerBox', Sort.asc);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QAfterSortBy>
      thenByUnitsPerBoxDesc() {
    return QueryBuilder.apply(this, (query) {
      return query.addSortBy(r'unitsPerBox', Sort.desc);
    });
  }
}

extension PalletCalculationQueryWhereDistinct
    on QueryBuilder<PalletCalculation, PalletCalculation, QDistinct> {
  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByAcceptedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'acceptedUnits');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByBoxesPerPallet() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'boxesPerPallet');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByCarrierType({bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'carrierType', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByCreatedAt() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'createdAt');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByExpectedUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'expectedUnits');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByFullPallets() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'fullPallets');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByIsManual() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'isManual');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByLooseUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'looseUnits');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByPhotoId() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'photoId');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByRemainderBoxes() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'remainderBoxes');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctBySessionId() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'sessionId');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct> distinctBySku(
      {bool caseSensitive = true}) {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'sku', caseSensitive: caseSensitive);
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByTotalUnits() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'totalUnits');
    });
  }

  QueryBuilder<PalletCalculation, PalletCalculation, QDistinct>
      distinctByUnitsPerBox() {
    return QueryBuilder.apply(this, (query) {
      return query.addDistinctBy(r'unitsPerBox');
    });
  }
}

extension PalletCalculationQueryProperty
    on QueryBuilder<PalletCalculation, PalletCalculation, QQueryProperty> {
  QueryBuilder<PalletCalculation, int, QQueryOperations> idProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'id');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      acceptedUnitsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'acceptedUnits');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      boxesPerPalletProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'boxesPerPallet');
    });
  }

  QueryBuilder<PalletCalculation, String?, QQueryOperations>
      carrierTypeProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'carrierType');
    });
  }

  QueryBuilder<PalletCalculation, DateTime, QQueryOperations>
      createdAtProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'createdAt');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      expectedUnitsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'expectedUnits');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      fullPalletsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'fullPallets');
    });
  }

  QueryBuilder<PalletCalculation, bool, QQueryOperations> isManualProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'isManual');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations> looseUnitsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'looseUnits');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations> photoIdProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'photoId');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      remainderBoxesProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'remainderBoxes');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations> sessionIdProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'sessionId');
    });
  }

  QueryBuilder<PalletCalculation, String?, QQueryOperations> skuProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'sku');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations> totalUnitsProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'totalUnits');
    });
  }

  QueryBuilder<PalletCalculation, int?, QQueryOperations>
      unitsPerBoxProperty() {
    return QueryBuilder.apply(this, (query) {
      return query.addPropertyName(r'unitsPerBox');
    });
  }
}
