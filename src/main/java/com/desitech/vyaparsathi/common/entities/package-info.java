/**
 * This package-info file is used to place Hibernate annotations
 * that apply to multiple entities within this package and its children,
 * such as a global Filter Definition.
 */
@org.hibernate.annotations.FilterDef(
        name = "shopFilter",
        parameters = @org.hibernate.annotations.ParamDef(name = "shopId", type = Long.class)
)
package com.desitech.vyaparsathi.common.entities;
