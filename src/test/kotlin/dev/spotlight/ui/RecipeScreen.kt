package dev.spotlight.ui

internal object RecipeScreenCheck {
    @JvmStatic fun main(args: Array<String>) {
        check((0..8).map { recipeIngredientAt(it, 2, 2, false, 4) } == listOf(0, 1, -1, 2, 3, -1, -1, -1, -1))
        check((0..8).map { recipeIngredientAt(it, 1, 3, false, 3) } == listOf(0, -1, -1, 1, -1, -1, 2, -1, -1))
        check((0..8).map { recipeIngredientAt(it, 2, 2, true, 4) } == listOf(0, 1, 2, 3, -1, -1, -1, -1, -1))
        check((0..8).map { recipeIngredientAt(it, 3, 3, false, 9) } == (0..8).toList())
        check(recipeIngredientAt(9, 3, 3, true, 9) == -1)
        check(recipeIngredientAt(0, 0, 3, false, 1) == -1)
        check(recipeIngredientAt(0, 1, 1, false, 0) == -1)
        println("Recipe grid layout checks passed")
    }
}
