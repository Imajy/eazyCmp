package com.aj.shared.parser

import kotlin.math.pow

/**
 * Lightweight formula resolver for cells that only expose equals-formulas.
 * Supports SUM/AVERAGE/COUNT/MIN/MAX/SUMIF/COUNTIF/SUMIFS/COUNTIFS/AVERAGEIF(S)/PRODUCT,
 * IF/IFS/IFERROR/IFNA/SWITCH/CHOOSE,
 * AND/OR/NOT, VLOOKUP/XLOOKUP, INDEX/MATCH/XMATCH, UNIQUE/FILTER/SORT, CONCAT/TEXTJOIN/SUBSTITUTE,
 * LEFT/RIGHT/MID/LEN/TRIM/UPPER/LOWER, ROUND/ABS/INT/MOD/POWER/VALUE,
 * ISBLANK/ISNUMBER/ISTEXT, LET, A1 refs, A1+B1 math.
 */
internal object SimpleFormulaEvaluator {

    fun evaluateGrid(grid: MutableMap<Int, MutableMap<Int, String>>, passes: Int = 6) {
        repeat(passes) {
            val updates = ArrayList<Triple<Int, Int, String>>()
            for ((r, row) in grid) {
                for ((c, v) in row) {
                    if (!v.startsWith("=")) continue
                    val resolved = eval(v, grid) ?: continue
                    if (resolved != v) updates += Triple(r, c, resolved)
                }
            }
            if (updates.isEmpty()) return
            for ((r, c, v) in updates) {
                grid.getOrPut(r) { mutableMapOf() }[c] = v
            }
        }
    }

    fun evaluateMatrix(matrix: List<MutableList<String>>, passes: Int = 4) {
        val grid = mutableMapOf<Int, MutableMap<Int, String>>()
        matrix.forEachIndexed { r, row ->
            row.forEachIndexed { c, v ->
                if (v.isNotEmpty()) grid.getOrPut(r) { mutableMapOf() }[c] = v
            }
        }
        evaluateGrid(grid, passes)
        for ((r, rowMap) in grid) {
            if (r !in matrix.indices) continue
            for ((c, v) in rowMap) {
                if (c in matrix[r].indices) matrix[r][c] = v
            }
        }
    }

    private fun eval(formula: String, grid: Map<Int, Map<Int, String>>): String? {
        val body = formula.removePrefix("=").trim()
        if (body.isEmpty()) return null
        return evalExpr(body, grid)
    }

    private fun evalExpr(body: String, grid: Map<Int, Map<Int, String>>): String? {
        // IF(cond, a, b)
        val iff = Regex("""^IF\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (iff != null) {
            val args = splitArgs(iff.groupValues[1])
            if (args.size < 2) return null
            val cond = evalExpr(args[0].trim(), grid) ?: args[0].trim()
            val truthy = isTruthy(cond)
            val chosen = (if (truthy) args.getOrNull(1) else args.getOrNull(2)).orEmpty()
            val cleaned = chosen.trim().removeSurrounding("\"")
            return evalExpr(cleaned, grid) ?: cleaned
        }

        // IFS(c1,v1,c2,v2,…)
        val ifs = Regex("""^IFS\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (ifs != null) {
            val args = splitArgs(ifs.groupValues[1])
            var i = 0
            while (i + 1 < args.size) {
                val cond = evalExpr(args[i].trim(), grid) ?: args[i].trim()
                if (isTruthy(cond)) {
                    val chosen = args[i + 1].trim().removeSurrounding("\"")
                    return evalExpr(chosen, grid) ?: chosen
                }
                i += 2
            }
            return "#N/A"
        }

        // SWITCH(expr, a, v1, b, v2, … [, default])
        val switchFn = Regex("""^SWITCH\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (switchFn != null) {
            val args = splitArgs(switchFn.groupValues[1])
            if (args.size < 3) return null
            val needle = (evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: args[0].trim().removeSurrounding("\"")).trim()
            var i = 1
            while (i + 1 < args.size) {
                val key = (evalExpr(args[i].trim().removeSurrounding("\""), grid)
                    ?: args[i].trim().removeSurrounding("\"")).trim()
                if (key.equals(needle, true)) {
                    val chosen = args[i + 1].trim().removeSurrounding("\"")
                    return evalExpr(chosen, grid) ?: chosen
                }
                i += 2
            }
            if (args.size % 2 == 0) {
                val fb = args.last().trim().removeSurrounding("\"")
                return evalExpr(fb, grid) ?: fb
            }
            return "#N/A"
        }

        // CHOOSE(index, v1, v2, …)
        val chooseFn = Regex("""^CHOOSE\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (chooseFn != null) {
            val args = splitArgs(chooseFn.groupValues[1])
            if (args.size < 2) return null
            val idx = (evalExpr(args[0].trim(), grid) ?: args[0].trim()).toIntOrNull() ?: return "#VALUE!"
            if (idx < 1 || idx >= args.size) return "#VALUE!"
            val chosen = args[idx].trim().removeSurrounding("\"")
            return evalExpr(chosen, grid) ?: chosen
        }

        val andOr = Regex("""^(AND|OR)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (andOr != null) {
            val name = andOr.groupValues[1].uppercase()
            val args = splitArgs(andOr.groupValues[2]).map {
                isTruthy(evalExpr(it.trim(), grid) ?: it.trim())
            }
            return when (name) {
                "AND" -> if (args.all { it }) "TRUE" else "FALSE"
                else -> if (args.any { it }) "TRUE" else "FALSE"
            }
        }
        val not = Regex("""^NOT\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (not != null) {
            val v = evalExpr(not.groupValues[1].trim(), grid) ?: not.groupValues[1].trim()
            return if (isTruthy(v)) "FALSE" else "TRUE"
        }

        // IFERROR / IFNA
        val ifErr = Regex("""^(IFERROR|IFNA)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (ifErr != null) {
            val kind = ifErr.groupValues[1].uppercase()
            val args = splitArgs(ifErr.groupValues[2])
            if (args.isEmpty()) return null
            val primary = evalExpr(args[0].trim(), grid)
            val isBad = when (kind) {
                "IFNA" -> primary == null || primary == "#N/A"
                else -> primary == null || primary.startsWith("#")
            }
            if (!isBad && primary != null) return primary
            val fb = args.getOrNull(1)?.trim().orEmpty()
            return evalExpr(fb.removeSurrounding("\""), grid) ?: fb.removeSurrounding("\"")
        }

        // VLOOKUP(lookup, A1:B10, colIndex, [rangeLookup])
        val vlookup = Regex("""^VLOOKUP\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (vlookup != null) {
            val args = splitArgs(vlookup.groupValues[1])
            if (args.size < 3) return null
            val needle = (evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: args[0].trim().removeSurrounding("\"")).trim()
            val range = args[1].trim()
            val colIndex = (evalExpr(args[2].trim(), grid) ?: args[2].trim()).toIntOrNull() ?: return "#N/A"
            val rangeLookup = when (val a = args.getOrNull(3)?.trim()?.uppercase()) {
                null, "", "TRUE", "1" -> true
                "FALSE", "0" -> false
                else -> true
            }
            return vlookup(needle, range, colIndex, exact = !rangeLookup, grid)
        }

        // XLOOKUP(lookup, lookupRange, returnRange) — exact match subset
        val xlookup = Regex("""^XLOOKUP\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (xlookup != null) {
            val args = splitArgs(xlookup.groupValues[1])
            if (args.size < 3) return null
            val needle = (evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: args[0].trim().removeSurrounding("\"")).trim()
            return xlookup(needle, args[1].trim(), args[2].trim(), grid)
        }

        // MATCH(lookup, A1:A10, [type]) → 1-based index
        val matchFn = Regex("""^MATCH\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (matchFn != null) {
            val args = splitArgs(matchFn.groupValues[1])
            if (args.size < 2) return null
            val needle = (evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: args[0].trim().removeSurrounding("\"")).trim()
            val matchType = args.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
            return match(needle, args[1].trim(), matchType, grid)
        }

        // XMATCH(lookup, range) → exact 1-based index
        val xmatch = Regex("""^XMATCH\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (xmatch != null) {
            val args = splitArgs(xmatch.groupValues[1])
            if (args.size < 2) return null
            val needle = (evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: args[0].trim().removeSurrounding("\"")).trim()
            return match(needle, args[1].trim(), matchType = 0, grid)
        }

        // IFBLANK(value, fallback)
        val ifBlank = Regex("""^IFBLANK\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (ifBlank != null) {
            val args = splitArgs(ifBlank.groupValues[1])
            if (args.isEmpty()) return null
            val primary = evalExpr(args[0].trim().removeSurrounding("\""), grid)
                ?: cellValue(args[0].trim(), grid)
                ?: args[0].trim().removeSurrounding("\"")
            if (primary.isNotBlank()) return primary
            val fb = args.getOrNull(1)?.trim().orEmpty()
            return evalExpr(fb.removeSurrounding("\""), grid) ?: fb.removeSurrounding("\"")
        }

        // LET(name, value, ..., calc) — simple local bindings (no nested LET)
        val letFn = Regex("""^LET\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (letFn != null) {
            val args = splitArgs(letFn.groupValues[1])
            if (args.size < 3 || args.size % 2 == 0) return null
            val bindings = linkedMapOf<String, String>()
            var i = 0
            while (i + 1 < args.lastIndex) {
                val name = args[i].trim()
                val raw = args[i + 1].trim()
                var value = evalExpr(raw.removeSurrounding("\""), grid) ?: raw.removeSurrounding("\"")
                for ((k, v) in bindings) {
                    value = value.replace(Regex("""\b${Regex.escape(k)}\b"""), v)
                }
                bindings[name] = value
                i += 2
            }
            var calc = args.last().trim().removeSurrounding("\"")
            for ((k, v) in bindings) {
                calc = calc.replace(Regex("""\b${Regex.escape(k)}\b"""), v)
            }
            return evalExpr(calc, grid) ?: calc
        }

        // LAMBDA(param1, param2, ..., calc)(arg1, arg2, ...) — single-shot apply (no named reuse)
        val lambdaApply = Regex(
            """^LAMBDA\((.+)\)\((.+)\)$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(body)
        if (lambdaApply != null) {
            val defArgs = splitArgs(lambdaApply.groupValues[1])
            val callArgs = splitArgs(lambdaApply.groupValues[2])
            if (defArgs.size < 2) return null
            val params = defArgs.dropLast(1).map { it.trim() }
            if (params.size != callArgs.size) return "#VALUE!"
            var calc = defArgs.last().trim().removeSurrounding("\"")
            params.forEachIndexed { idx, name ->
                val raw = callArgs[idx].trim()
                val value = evalExpr(raw.removeSurrounding("\""), grid) ?: raw.removeSurrounding("\"")
                calc = calc.replace(Regex("""\b${Regex.escape(name)}\b"""), value)
            }
            return evalExpr(calc, grid) ?: calc
        }

        // INDEX(range, row, [col]) — 1-based
        val indexFn = Regex("""^INDEX\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (indexFn != null) {
            val args = splitArgs(indexFn.groupValues[1])
            if (args.size < 2) return null
            val row = (evalExpr(args[1].trim(), grid) ?: args[1].trim()).toIntOrNull() ?: return "#REF!"
            val col = args.getOrNull(2)?.let {
                (evalExpr(it.trim(), grid) ?: it.trim()).toIntOrNull()
            } ?: 1
            return index(args[0].trim(), row, col, grid)
        }

        // UNIQUE(range) → first unique joined by ", " (viewer/scalar fallback)
        val uniqueFn = Regex("""^UNIQUE\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (uniqueFn != null) {
            val args = splitArgs(uniqueFn.groupValues[1])
            if (args.isEmpty()) return null
            val cells = rangeCells(args[0].trim(), grid) ?: return null
            val seen = linkedSetOf<String>()
            for (c in cells) {
                if (c.isNotEmpty()) seen += c
            }
            return seen.joinToString(", ")
        }

        // SORT(range, [order]) order 1 asc (default), -1 desc — joined
        val sortFn = Regex("""^SORT\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (sortFn != null) {
            val args = splitArgs(sortFn.groupValues[1])
            if (args.isEmpty()) return null
            val cells = rangeCells(args[0].trim(), grid)?.filter { it.isNotEmpty() } ?: return null
            val order = args.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
            val sorted = cells.sortedWith { a, b ->
                val an = a.toDoubleOrNull()
                val bn = b.toDoubleOrNull()
                val cmp = when {
                    an != null && bn != null -> an.compareTo(bn)
                    else -> a.compareTo(b, ignoreCase = true)
                }
                if (order < 0) -cmp else cmp
            }
            return sorted.joinToString(", ")
        }

        // ISBLANK / ISNUMBER / ISTEXT
        val isFn = Regex("""^(ISBLANK|ISNUMBER|ISTEXT)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (isFn != null) {
            val kind = isFn.groupValues[1].uppercase()
            val arg = isFn.groupValues[2].trim().removeSurrounding("\"")
            val v = evalExpr(arg, grid) ?: cellValue(arg, grid) ?: arg
            return when (kind) {
                "ISBLANK" -> if (v.isBlank()) "TRUE" else "FALSE"
                "ISNUMBER" -> if (v.toDoubleOrNull() != null) "TRUE" else "FALSE"
                else -> if (v.isNotBlank() && v.toDoubleOrNull() == null &&
                    !v.equals("TRUE", true) && !v.equals("FALSE", true)
                ) "TRUE" else "FALSE"
            }
        }

        // TEXTJOIN(delim, ignoreEmpty, text1, …)
        val textJoin = Regex("""^TEXTJOIN\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (textJoin != null) {
            val args = splitArgs(textJoin.groupValues[1])
            if (args.size < 3) return null
            val delim = textArg(args[0], grid).orEmpty()
            val ignoreEmpty = isTruthy(evalExpr(args[1].trim(), grid) ?: args[1].trim())
            val parts = ArrayList<String>()
            for (a in args.drop(2)) {
                for (p in expandRefs(a.trim(), grid)) {
                    if (!ignoreEmpty || p.isNotEmpty()) parts += p
                }
            }
            return parts.joinToString(delim)
        }

        // SUBSTITUTE(text, old, new, [instance])
        val subst = Regex("""^SUBSTITUTE\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (subst != null) {
            val args = splitArgs(subst.groupValues[1])
            if (args.size < 3) return null
            val text = textArg(args[0], grid).orEmpty()
            val old = textArg(args[1], grid).orEmpty()
            val neu = textArg(args[2], grid).orEmpty()
            if (old.isEmpty()) return text
            val instance = args.getOrNull(3)?.let {
                (evalExpr(it.trim(), grid) ?: it.trim()).toIntOrNull()
            }
            return if (instance == null || instance <= 0) {
                text.replace(old, neu)
            } else {
                var seen = 0
                buildString {
                    var i = 0
                    while (i < text.length) {
                        if (text.startsWith(old, i)) {
                            seen++
                            if (seen == instance) {
                                append(neu)
                                i += old.length
                                continue
                            }
                        }
                        append(text[i])
                        i++
                    }
                }
            }
        }

        // ROUND / ABS / INT / MOD / POWER / VALUE / PRODUCT
        val mathFn = Regex(
            """^(ROUND|ABS|INT|MOD|POWER|VALUE|PRODUCT)\((.+)\)$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(body)
        if (mathFn != null) {
            val name = mathFn.groupValues[1].uppercase()
            val args = splitArgs(mathFn.groupValues[2])
            return when (name) {
                "ABS" -> {
                    val n = (evalExpr(args.getOrNull(0)?.trim().orEmpty(), grid) ?: args.getOrNull(0))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    formatNum(kotlin.math.abs(n))
                }
                "INT" -> {
                    val n = (evalExpr(args.getOrNull(0)?.trim().orEmpty(), grid) ?: args.getOrNull(0))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    kotlin.math.floor(n).toLong().toString()
                }
                "VALUE" -> {
                    val t = textArg(args.getOrNull(0), grid) ?: return "#VALUE!"
                    t.toDoubleOrNull()?.let { formatNum(it) } ?: "#VALUE!"
                }
                "ROUND" -> {
                    val n = (evalExpr(args.getOrNull(0)?.trim().orEmpty(), grid) ?: args.getOrNull(0))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    val digits = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toIntOrNull() ?: 0
                    val factor = when {
                        digits >= 0 -> {
                            var f = 1.0
                            repeat(digits) { f *= 10.0 }
                            f
                        }
                        else -> {
                            var f = 1.0
                            repeat(-digits) { f *= 10.0 }
                            1.0 / f
                        }
                    }
                    formatNum(kotlin.math.round(n * factor) / factor)
                }
                "MOD" -> {
                    val a = (evalExpr(args.getOrNull(0)?.trim().orEmpty(), grid) ?: args.getOrNull(0))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    val b = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    if (b == 0.0) return "#DIV/0!"
                    formatNum(a % b)
                }
                "POWER" -> {
                    val a = (evalExpr(args.getOrNull(0)?.trim().orEmpty(), grid) ?: args.getOrNull(0))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    val b = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toDoubleOrNull() ?: return "#VALUE!"
                    formatNum(a.pow(b))
                }
                "PRODUCT" -> {
                    val n = nums(args, grid)
                    if (n.isEmpty()) "0" else formatNum(n.fold(1.0) { acc, v -> acc * v })
                }
                else -> null
            }
        }

        // FILTER(range, include) — scalar-friendly; matching values joined by ", "
        val filterFn = Regex("""^FILTER\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (filterFn != null) {
            val args = splitArgs(filterFn.groupValues[1])
            if (args.size < 2) return null
            val values = rangeCells(args[0].trim(), grid) ?: return null
            val includeExpr = args[1].trim()
            val cmp = Regex("""^(.+?)\s*(<=|>=|<>|=|<|>)\s*(.+)$""").matchEntire(includeExpr)
            val out = ArrayList<String>()
            if (cmp != null) {
                val leftCells = rangeCells(cmp.groupValues[1].trim(), grid) ?: return null
                val criteria = cmp.groupValues[2] + cmp.groupValues[3].trim().removeSurrounding("\"")
                val n = minOf(values.size, leftCells.size)
                for (i in 0 until n) {
                    if (matchesCriteria(leftCells[i], criteria)) out += values[i]
                }
            } else {
                val includeCells = rangeCells(includeExpr, grid) ?: return null
                val n = minOf(values.size, includeCells.size)
                for (i in 0 until n) {
                    if (isTruthy(includeCells[i])) out += values[i]
                }
            }
            return if (out.isEmpty()) "#CALC!" else out.joinToString(", ")
        }

        val sumIf = Regex("""^(SUMIF|COUNTIF|AVERAGEIF)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (sumIf != null) {
            val name = sumIf.groupValues[1].uppercase()
            val args = splitArgs(sumIf.groupValues[2])
            if (args.size < 2) return null
            val criteria = args[1].trim().removeSurrounding("\"")
            val rangeCells = rangeCells(args[0].trim(), grid) ?: return null
            val sumRange = if ((name == "SUMIF" || name == "AVERAGEIF") && args.size >= 3) {
                rangeCells(args[2].trim(), grid) ?: rangeCells
            } else rangeCells
            var count = 0
            var sum = 0.0
            val n = minOf(rangeCells.size, sumRange.size)
            for (i in 0 until n) {
                if (matchesCriteria(rangeCells[i], criteria)) {
                    count++
                    sumRange[i].toDoubleOrNull()?.let { sum += it }
                }
            }
            return when (name) {
                "COUNTIF" -> count.toString()
                "AVERAGEIF" -> if (count == 0) "#DIV/0!" else formatNum(sum / count)
                else -> formatNum(sum)
            }
        }

        // COUNTIFS / SUMIFS / AVERAGEIFS — pairs of (criteriaRange, criteria) [+ sumRange first for SUMIFS]
        val multiIf = Regex("""^(COUNTIFS|SUMIFS|AVERAGEIFS)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (multiIf != null) {
            val name = multiIf.groupValues[1].uppercase()
            val args = splitArgs(multiIf.groupValues[2])
            when (name) {
                "COUNTIFS" -> {
                    if (args.size < 2 || args.size % 2 != 0) return null
                    val pairs = args.chunked(2).map { (r, c) ->
                        (rangeCells(r.trim(), grid) ?: return null) to c.trim().removeSurrounding("\"")
                    }
                    val n = pairs.minOf { it.first.size }
                    var count = 0
                    for (i in 0 until n) {
                        if (pairs.all { (cells, crit) -> matchesCriteria(cells[i], crit) }) count++
                    }
                    return count.toString()
                }
                else -> {
                    // SUMIFS/AVERAGEIFS(sumRange, critRange1, crit1, …)
                    if (args.size < 3 || args.size % 2 == 0) return null
                    val sumRange = rangeCells(args[0].trim(), grid) ?: return null
                    val pairs = args.drop(1).chunked(2).map { (r, c) ->
                        (rangeCells(r.trim(), grid) ?: return null) to c.trim().removeSurrounding("\"")
                    }
                    val n = minOf(sumRange.size, pairs.minOf { it.first.size })
                    var count = 0
                    var sum = 0.0
                    for (i in 0 until n) {
                        if (pairs.all { (cells, crit) -> matchesCriteria(cells[i], crit) }) {
                            count++
                            sumRange[i].toDoubleOrNull()?.let { sum += it }
                        }
                    }
                    return when (name) {
                        "AVERAGEIFS" -> if (count == 0) "#DIV/0!" else formatNum(sum / count)
                        else -> formatNum(sum)
                    }
                }
            }
        }

        val fn = Regex(
            """^(SUM|AVERAGE|AVG|COUNT|MIN|MAX|CONCAT|CONCATENATE|LEFT|RIGHT|MID|LEN|TRIM|UPPER|LOWER)\((.+)\)$""",
            RegexOption.IGNORE_CASE,
        ).matchEntire(body)
        if (fn != null) {
            val name = fn.groupValues[1].uppercase()
            val args = splitArgs(fn.groupValues[2])
            return when (name) {
                "SUM" -> formatNum(nums(args, grid).sum())
                "AVERAGE", "AVG" -> {
                    val n = nums(args, grid)
                    if (n.isEmpty()) "0" else formatNum(n.average())
                }
                "COUNT" -> nums(args, grid).size.toString()
                "MIN" -> formatNum(nums(args, grid).minOrNull() ?: 0.0)
                "MAX" -> formatNum(nums(args, grid).maxOrNull() ?: 0.0)
                "CONCAT", "CONCATENATE" -> args.joinToString("") {
                    evalExpr(it.trim().removeSurrounding("\""), grid)
                        ?: it.trim().removeSurrounding("\"")
                }
                "LEFT" -> {
                    val s = textArg(args.getOrNull(0), grid) ?: return null
                    val n = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toIntOrNull() ?: 1
                    s.take(n.coerceAtLeast(0))
                }
                "RIGHT" -> {
                    val s = textArg(args.getOrNull(0), grid) ?: return null
                    val n = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toIntOrNull() ?: 1
                    s.takeLast(n.coerceAtLeast(0))
                }
                "MID" -> {
                    val s = textArg(args.getOrNull(0), grid) ?: return null
                    val start = (evalExpr(args.getOrNull(1)?.trim().orEmpty(), grid) ?: args.getOrNull(1))
                        ?.toIntOrNull() ?: 1
                    val len = (evalExpr(args.getOrNull(2)?.trim().orEmpty(), grid) ?: args.getOrNull(2))
                        ?.toIntOrNull() ?: 0
                    s.drop((start - 1).coerceAtLeast(0)).take(len.coerceAtLeast(0))
                }
                "LEN" -> (textArg(args.getOrNull(0), grid) ?: "").length.toString()
                "TRIM" -> textArg(args.getOrNull(0), grid)?.trim()
                "UPPER" -> textArg(args.getOrNull(0), grid)?.uppercase()
                "LOWER" -> textArg(args.getOrNull(0), grid)?.lowercase()
                else -> null
            }
        }

        // Comparison: A1>B1 / A1="x"
        val cmp = Regex("""^(.+?)\s*(<=|>=|<>|=|<|>)\s*(.+)$""").matchEntire(body)
        if (cmp != null && !body.contains('(')) {
            val l = evalExpr(cmp.groupValues[1].trim(), grid) ?: cmp.groupValues[1].trim()
            val r = evalExpr(cmp.groupValues[3].trim().removeSurrounding("\""), grid)
                ?: cmp.groupValues[3].trim().removeSurrounding("\"")
            val op = cmp.groupValues[2]
            val ln = l.toDoubleOrNull()
            val rn = r.toDoubleOrNull()
            val ok = if (ln != null && rn != null) {
                when (op) {
                    "=" -> ln == rn
                    "<>" -> ln != rn
                    "<" -> ln < rn
                    ">" -> ln > rn
                    "<=" -> ln <= rn
                    ">=" -> ln >= rn
                    else -> false
                }
            } else {
                when (op) {
                    "=" -> l.equals(r, true)
                    "<>" -> !l.equals(r, true)
                    else -> l.compareTo(r, true).let {
                        when (op) {
                            "<" -> it < 0
                            ">" -> it > 0
                            "<=" -> it <= 0
                            ">=" -> it >= 0
                            else -> false
                        }
                    }
                }
            }
            return if (ok) "TRUE" else "FALSE"
        }

        // ROW(ref) / COLUMN(ref) — 1-based
        val rowCol = Regex("""^(ROW|COLUMN)\((.*)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (rowCol != null) {
            val kind = rowCol.groupValues[1].uppercase()
            val arg = rowCol.groupValues[2].trim().removeSurrounding("\"")
            if (arg.isEmpty()) return null
            val ref = parseRef(arg.substringBefore(':').trim()) ?: return "#REF!"
            return if (kind == "ROW") (ref.first + 1).toString() else (ref.second + 1).toString()
        }

        // HYPERLINK(url, [friendly])
        val hyper = Regex("""^HYPERLINK\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (hyper != null) {
            val args = splitArgs(hyper.groupValues[1])
            if (args.isEmpty()) return null
            val friendly = args.getOrNull(1)?.trim()?.removeSurrounding("\"")
            if (!friendly.isNullOrBlank()) {
                return evalExpr(friendly, grid) ?: friendly
            }
            return textArg(args[0], grid)
        }

        // INDIRECT("A1") / INDIRECT(A1) where A1 holds a ref
        val indirect = Regex("""^INDIRECT\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (indirect != null) {
            val raw = indirect.groupValues[1].trim().removeSurrounding("\"")
            val refText = evalExpr(raw, grid) ?: cellValue(raw, grid) ?: raw
            return cellValue(refText.trim().removeSurrounding("\""), grid)
        }

        // N(value) / T(value)
        val nt = Regex("""^(N|T)\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (nt != null) {
            val kind = nt.groupValues[1].uppercase()
            val v = evalExpr(nt.groupValues[2].trim().removeSurrounding("\""), grid)
                ?: cellValue(nt.groupValues[2].trim(), grid)
                ?: nt.groupValues[2].trim().removeSurrounding("\"")
            return when (kind) {
                "N" -> v.toDoubleOrNull()?.let { formatNum(it) }
                    ?: if (v.equals("TRUE", true)) "1" else if (v.equals("FALSE", true)) "0" else "0"
                else -> if (v.toDoubleOrNull() != null || v.equals("TRUE", true) || v.equals("FALSE", true)) "" else v
            }
        }

        // LEN already covered; TYPE(value) → 1 number, 2 text, 4 bool
        val typeFn = Regex("""^TYPE\((.+)\)$""", RegexOption.IGNORE_CASE).matchEntire(body)
        if (typeFn != null) {
            val v = evalExpr(typeFn.groupValues[1].trim().removeSurrounding("\""), grid)
                ?: cellValue(typeFn.groupValues[1].trim(), grid)
                ?: typeFn.groupValues[1].trim().removeSurrounding("\"")
            return when {
                v.equals("TRUE", true) || v.equals("FALSE", true) -> "4"
                v.toDoubleOrNull() != null -> "1"
                else -> "2"
            }
        }

        val bin = Regex(
            """^([A-Za-z]+\d+|-?\d+(?:\.\d+)?)\s*([+\-*/])\s*([A-Za-z]+\d+|-?\d+(?:\.\d+)?)$""",
        ).matchEntire(body)
        if (bin != null) {
            fun atom(raw: String): Double? =
                raw.toDoubleOrNull() ?: cellValue(raw, grid)?.toDoubleOrNull()
            val a = atom(bin.groupValues[1]) ?: return null
            val b = atom(bin.groupValues[3]) ?: return null
            val r = when (bin.groupValues[2]) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b == 0.0) return "#DIV/0!" else a / b
                else -> return null
            }
            return formatNum(r)
        }

        if (Regex("""^[A-Za-z]+\d+$""").matches(body)) {
            return cellValue(body, grid)
        }
        if (body.equals("TRUE", true) || body.equals("FALSE", true)) return body.uppercase()
        body.toDoubleOrNull()?.let { return formatNum(it) }
        return null
    }

    private fun nums(args: List<String>, grid: Map<Int, Map<Int, String>>): List<Double> =
        args.flatMap { expandRefs(it.trim(), grid) }.mapNotNull { it.toDoubleOrNull() }

    private fun textArg(raw: String?, grid: Map<Int, Map<Int, String>>): String? {
        if (raw == null) return null
        val t = raw.trim().removeSurrounding("\"")
        return evalExpr(t, grid) ?: cellValue(t, grid) ?: t
    }

    private fun isTruthy(v: String): Boolean {
        val t = v.trim()
        if (t.equals("TRUE", true)) return true
        if (t.equals("FALSE", true) || t.isEmpty() || t == "0") return false
        t.toDoubleOrNull()?.let { return it != 0.0 }
        return true
    }

    private fun splitArgs(expr: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var depth = 0
        var inQuote = false
        for (ch in expr) {
            when {
                ch == '"' -> {
                    inQuote = !inQuote
                    cur.append(ch)
                }
                !inQuote && ch == '(' -> {
                    depth++; cur.append(ch)
                }
                !inQuote && ch == ')' -> {
                    depth--; cur.append(ch)
                }
                !inQuote && ch == ',' && depth == 0 -> {
                    out += cur.toString(); cur.clear()
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    private fun expandRefs(expr: String, grid: Map<Int, Map<Int, String>>): List<String> {
        val out = ArrayList<String>()
        for (part in splitArgs(expr).ifEmpty { listOf(expr) }) {
            val p = part.trim()
            if (p.contains(':')) {
                val bits = p.split(':', limit = 2)
                val c1 = parseRef(bits[0].trim()) ?: continue
                val c2 = parseRef(bits[1].trim()) ?: continue
                for (r in minOf(c1.first, c2.first)..maxOf(c1.first, c2.first)) {
                    for (c in minOf(c1.second, c2.second)..maxOf(c1.second, c2.second)) {
                        out += grid[r]?.get(c).orEmpty()
                    }
                }
            } else {
                out += evalExpr(p, grid) ?: cellValue(p, grid).orEmpty()
            }
        }
        return out
    }

    private fun vlookup(
        needle: String,
        range: String,
        colIndex: Int,
        exact: Boolean,
        grid: Map<Int, Map<Int, String>>,
    ): String {
        if (colIndex < 1 || !range.contains(':')) return "#N/A"
        val bits = range.split(':', limit = 2)
        val c1 = parseRef(bits[0].trim()) ?: return "#N/A"
        val c2 = parseRef(bits[1].trim()) ?: return "#N/A"
        val r0 = minOf(c1.first, c2.first)
        val r1 = maxOf(c1.first, c2.first)
        val keyCol = minOf(c1.second, c2.second)
        val valueCol = keyCol + colIndex - 1
        if (valueCol > maxOf(c1.second, c2.second)) return "#N/A"
        var approx: String? = null
        for (r in r0..r1) {
            val key = grid[r]?.get(keyCol).orEmpty()
            if (exact) {
                if (key.equals(needle, ignoreCase = true)) {
                    return grid[r]?.get(valueCol).orEmpty()
                }
            } else {
                val kn = key.toDoubleOrNull()
                val nn = needle.toDoubleOrNull()
                if (kn != null && nn != null) {
                    if (kn == nn) return grid[r]?.get(valueCol).orEmpty()
                    if (kn < nn) approx = grid[r]?.get(valueCol).orEmpty()
                } else if (key.equals(needle, ignoreCase = true)) {
                    return grid[r]?.get(valueCol).orEmpty()
                }
            }
        }
        return if (exact) "#N/A" else approx ?: "#N/A"
    }

    private fun xlookup(
        needle: String,
        lookupRange: String,
        returnRange: String,
        grid: Map<Int, Map<Int, String>>,
    ): String {
        val keys = rangeCells(lookupRange, grid) ?: return "#N/A"
        val vals = rangeCells(returnRange, grid) ?: return "#N/A"
        val n = minOf(keys.size, vals.size)
        for (i in 0 until n) {
            if (keys[i].equals(needle, ignoreCase = true)) return vals[i]
        }
        return "#N/A"
    }

    private fun match(
        needle: String,
        range: String,
        matchType: Int,
        grid: Map<Int, Map<Int, String>>,
    ): String {
        val cells = rangeCells(range, grid) ?: return "#N/A"
        when (matchType) {
            0 -> {
                val i = cells.indexOfFirst { it.equals(needle, ignoreCase = true) }
                return if (i >= 0) (i + 1).toString() else "#N/A"
            }
            1 -> {
                val nn = needle.toDoubleOrNull()
                var best = -1
                cells.forEachIndexed { i, v ->
                    val vn = v.toDoubleOrNull()
                    if (nn != null && vn != null) {
                        if (vn <= nn) best = i
                    } else if (v <= needle) best = i
                }
                return if (best >= 0) (best + 1).toString() else "#N/A"
            }
            -1 -> {
                val nn = needle.toDoubleOrNull()
                cells.forEachIndexed { i, v ->
                    val vn = v.toDoubleOrNull()
                    if (nn != null && vn != null) {
                        if (vn >= nn) return (i + 1).toString()
                    } else if (v >= needle) return (i + 1).toString()
                }
                return "#N/A"
            }
            else -> return "#N/A"
        }
    }

    private fun index(
        range: String,
        row: Int,
        col: Int,
        grid: Map<Int, Map<Int, String>>,
    ): String {
        if (row < 1 || col < 1 || !range.contains(':')) return "#REF!"
        val bits = range.split(':', limit = 2)
        val c1 = parseRef(bits[0].trim()) ?: return "#REF!"
        val c2 = parseRef(bits[1].trim()) ?: return "#REF!"
        val r0 = minOf(c1.first, c2.first)
        val c0 = minOf(c1.second, c2.second)
        val r1 = maxOf(c1.first, c2.first)
        val cMax = maxOf(c1.second, c2.second)
        val rr = r0 + row - 1
        val cc = c0 + col - 1
        if (rr > r1 || cc > cMax) return "#REF!"
        return grid[rr]?.get(cc).orEmpty()
    }

    private fun matchesCriteria(value: String, criteria: String): Boolean {
        val c = criteria.trim()
        val ops = listOf("<>", ">=", "<=", ">", "<", "=")
        for (op in ops) {
            if (c.startsWith(op)) {
                val rhs = c.removePrefix(op).trim().removeSurrounding("\"")
                val ln = value.toDoubleOrNull()
                val rn = rhs.toDoubleOrNull()
                return if (ln != null && rn != null) {
                    when (op) {
                        "=" -> ln == rn
                        "<>" -> ln != rn
                        "<" -> ln < rn
                        ">" -> ln > rn
                        "<=" -> ln <= rn
                        ">=" -> ln >= rn
                        else -> false
                    }
                } else {
                    when (op) {
                        "=" -> value.equals(rhs, true)
                        "<>" -> !value.equals(rhs, true)
                        else -> value.compareTo(rhs, true).let {
                            when (op) {
                                "<" -> it < 0
                                ">" -> it > 0
                                "<=" -> it <= 0
                                ">=" -> it >= 0
                                else -> false
                            }
                        }
                    }
                }
            }
        }
        return if (c.contains('*') || c.contains('?')) {
            val rx = Regex(
                "^" + Regex.escape(c).replace("\\*", ".*").replace("\\?", ".") + "$",
                RegexOption.IGNORE_CASE,
            )
            rx.matches(value)
        } else {
            value.equals(c, true)
        }
    }

    private fun rangeCells(range: String, grid: Map<Int, Map<Int, String>>): List<String>? {
        if (!range.contains(':')) {
            val one = cellValue(range, grid) ?: return null
            return listOf(one)
        }
        val bits = range.split(':', limit = 2)
        val c1 = parseRef(bits[0].trim()) ?: return null
        val c2 = parseRef(bits[1].trim()) ?: return null
        val out = ArrayList<String>()
        for (r in minOf(c1.first, c2.first)..maxOf(c1.first, c2.first)) {
            for (c in minOf(c1.second, c2.second)..maxOf(c1.second, c2.second)) {
                out += grid[r]?.get(c).orEmpty()
            }
        }
        return out
    }

    private fun cellValue(ref: String, grid: Map<Int, Map<Int, String>>): String? {
        val rc = parseRef(ref) ?: return null
        return grid[rc.first]?.get(rc.second)
    }

    private fun parseRef(ref: String): Pair<Int, Int>? {
        val m = Regex("""^([A-Za-z]+)(\d+)$""").matchEntire(ref.trim()) ?: return null
        var col = 0
        for (ch in m.groupValues[1].uppercase()) {
            col = col * 26 + (ch.code - 'A'.code + 1)
        }
        val row = m.groupValues[2].toIntOrNull() ?: return null
        return (row - 1) to (col - 1)
    }

    private fun formatNum(n: Double): String =
        if (n == n.toLong().toDouble()) n.toLong().toString()
        else n.toString().trimEnd('0').trimEnd('.')
}
