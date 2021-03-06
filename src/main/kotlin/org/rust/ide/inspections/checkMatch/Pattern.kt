/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.checkMatch

import org.rust.lang.core.psi.RsEnumVariant
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsFieldsOwner
import org.rust.lang.core.psi.ext.findInScope
import org.rust.lang.core.psi.ext.parentEnum
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.ty.TyTuple
import org.rust.lang.core.types.ty.TyUnknown

data class Pattern(val ty: Ty, val kind: PatternKind) {
    fun text(ctx: RsElement?): String =
        when (kind) {
            is PatternKind.Wild -> "_"

            is PatternKind.Binding -> kind.name

            is PatternKind.Variant -> {
                val variantName = kind.variant.name.orEmpty()
                val itemName = kind.item.name

                val variantInScope = ctx?.findInScope(variantName)
                    ?.takeIf { (it as? RsEnumVariant)?.parentEnum == kind.item }

                // if the variant is already in scope, it can be used as just `A` instead of full `MyEnum::A`
                val name = if (variantInScope != null) {
                    variantName
                } else {
                    "$itemName::$variantName"
                }
                val initializer = kind.variant.initializer(kind.subPatterns, ctx)
                "$name$initializer"
            }

            is PatternKind.Leaf -> {
                val subPatterns = kind.subPatterns
                when (ty) {
                    is TyTuple -> subPatterns.joinToString(", ", "(", ")") { it.text(ctx) }
                    is TyAdt -> {
                        val name = (ty.item as? RsStructItem)?.name ?: (ty.item as? RsEnumVariant)?.name
                        val initializer = (ty.item as RsFieldsOwner).initializer(subPatterns, ctx)
                        "$name$initializer"
                    }
                    else -> ""
                }
            }

            is PatternKind.Range -> "${kind.lc}${if (kind.isInclusive) ".." else "..="}${kind.rc}"

            is PatternKind.Deref -> "&${kind.subPattern.text(ctx)}"

            is PatternKind.Const -> kind.value.toString()

            is PatternKind.Slice -> TODO()

            is PatternKind.Array -> TODO()
        }

    val constructors: List<Constructor>?
        get() = when (kind) {
            PatternKind.Wild, is PatternKind.Binding -> null
            is PatternKind.Variant -> listOf(Constructor.Variant(kind.variant))
            is PatternKind.Leaf, is PatternKind.Deref -> listOf(Constructor.Single)
            is PatternKind.Const -> listOf(Constructor.ConstantValue(kind.value))
            is PatternKind.Range -> listOf(Constructor.ConstantRange(kind.lc, kind.rc, kind.isInclusive))
            is PatternKind.Slice -> TODO()
            is PatternKind.Array -> TODO()
        }

    companion object {
        val Wild: Pattern get() = Pattern(TyUnknown, PatternKind.Wild)
    }
}

private fun RsFieldsOwner.initializer(subPatterns: List<Pattern>, ctx: RsElement?): String = when {
    blockFields != null -> {
        subPatterns.withIndex().joinToString(",", "{", "}") { (index, pattern) ->
            "${blockFields!!.namedFieldDeclList[index].name}: ${pattern.text(ctx)}"
        }
    }
    tupleFields != null -> subPatterns.joinToString(",", "(", ")") { it.text(ctx) }
    else -> ""
}
