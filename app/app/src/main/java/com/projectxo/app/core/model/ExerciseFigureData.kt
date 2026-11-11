package com.projectxo.app.core.model

/**
 * Фигуры упражнений, стиль «Линия» (Р75). Данные поз сгенерированы из `assets/exercise-figures.json`.
 * Вид сбоку, холст 160×100, пол y = 88. Ключ: имя упражнения в [Exercise], для отжиманий «Pushups/Wall», «Pushups/Knees», «Pushups/Classic».
 */
object ExerciseFigureData {
    internal val specs: Map<String, FigureSpec> = mapOf(
        "Pushups/Wall" to FigureSpec(
            a = FigurePose(Pt(102.0f, 26.0f), Pt(93.6f, 48.8f), listOf(Pt(112.0f, 31.0f), Pt(122.0f, 34.0f)), listOf(Pt(86.8f, 67.4f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            b = FigurePose(Pt(112.0f, 36.0f), Pt(99.8f, 55.0f), listOf(Pt(116.0f, 48.0f), Pt(124.0f, 40.0f)), listOf(Pt(89.9f, 70.5f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            deco = FigureDeco.Wall,
            scale = 1f,
        ),
        "Pushups/Knees" to FigureSpec(
            a = FigurePose(Pt(108.0f, 64.0f), Pt(90.0f, 75.0f), listOf(Pt(108.0f, 76.0f), Pt(108.0f, 88.0f)), listOf(Pt(72.0f, 86.0f), Pt(52.0f, 78.0f), Pt(47.0f, 76.0f))),
            b = FigurePose(Pt(108.0f, 78.0f), Pt(90.0f, 82.0f), listOf(Pt(98.0f, 82.0f), Pt(108.0f, 88.0f)), listOf(Pt(72.0f, 86.0f), Pt(52.0f, 78.0f), Pt(47.0f, 76.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Pushups/Classic" to FigureSpec(
            a = FigurePose(Pt(108.0f, 64.0f), Pt(80.6f, 72.4f), listOf(Pt(108.0f, 76.0f), Pt(108.0f, 88.0f)), listOf(Pt(57.6f, 79.4f), Pt(36.0f, 86.0f), Pt(31.0f, 88.0f))),
            b = FigurePose(Pt(108.0f, 78.0f), Pt(80.6f, 81.0f), listOf(Pt(98.0f, 82.0f), Pt(108.0f, 88.0f)), listOf(Pt(57.6f, 83.6f), Pt(36.0f, 86.0f), Pt(31.0f, 88.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Dips" to FigureSpec(
            a = FigurePose(Pt(56.0f, 38.0f), Pt(66.0f, 60.0f), listOf(Pt(56.0f, 50.0f), Pt(56.0f, 62.0f)), listOf(Pt(84.0f, 66.0f), Pt(98.0f, 86.0f), Pt(106.0f, 86.0f))),
            b = FigurePose(Pt(56.0f, 52.0f), Pt(66.0f, 74.0f), listOf(Pt(44.0f, 56.0f), Pt(56.0f, 62.0f)), listOf(Pt(86.0f, 70.0f), Pt(98.0f, 86.0f), Pt(106.0f, 86.0f))),
            deco = FigureDeco.Bench,
            scale = 1f,
        ),
        "Pullups" to FigureSpec(
            a = FigurePose(Pt(80.0f, 36.0f), Pt(80.0f, 60.0f), listOf(Pt(80.0f, 24.0f), Pt(80.0f, 12.0f)), listOf(Pt(92.0f, 74.0f), Pt(78.0f, 84.0f), Pt(72.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 19.0f), Pt(80.0f, 43.0f), listOf(Pt(91.0f, 19.0f), Pt(80.0f, 12.0f)), listOf(Pt(92.0f, 57.0f), Pt(78.0f, 65.0f), Pt(72.0f, 67.0f))),
            deco = FigureDeco.Bar,
            scale = 1f,
        ),
        "Superman" to FigureSpec(
            a = FigurePose(Pt(86.0f, 84.0f), Pt(62.0f, 84.0f), listOf(Pt(98.0f, 76.0f), Pt(111.0f, 72.0f)), listOf(Pt(42.0f, 84.0f), Pt(22.0f, 84.0f), Pt(17.0f, 85.0f))),
            b = FigurePose(Pt(86.0f, 80.0f), Pt(62.0f, 84.0f), listOf(Pt(98.0f, 70.0f), Pt(110.0f, 62.0f)), listOf(Pt(42.0f, 80.0f), Pt(23.0f, 74.0f), Pt(18.0f, 72.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Hinge" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(86.0f, 30.0f), Pt(76.0f, 32.0f)), listOf(Pt(80.0f, 66.0f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            b = FigurePose(Pt(94.0f, 40.0f), Pt(72.0f, 48.0f), listOf(Pt(98.0f, 50.0f), Pt(88.0f, 50.0f)), listOf(Pt(77.0f, 68.0f), Pt(78.0f, 86.0f), Pt(87.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Squats" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(91.0f, 24.0f), Pt(102.0f, 24.0f)), listOf(Pt(80.0f, 66.0f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            b = FigurePose(Pt(86.0f, 46.0f), Pt(72.0f, 66.0f), listOf(Pt(97.0f, 46.0f), Pt(108.0f, 46.0f)), listOf(Pt(91.0f, 68.0f), Pt(78.0f, 86.0f), Pt(87.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "JumpSquat" to FigureSpec(
            a = FigurePose(Pt(86.0f, 46.0f), Pt(72.0f, 66.0f), listOf(Pt(97.0f, 46.0f), Pt(108.0f, 46.0f)), listOf(Pt(91.0f, 68.0f), Pt(78.0f, 86.0f), Pt(87.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 16.0f), Pt(80.0f, 40.0f), listOf(Pt(88.0f, 12.0f), Pt(98.0f, 6.0f)), listOf(Pt(80.0f, 60.0f), Pt(80.0f, 78.0f), Pt(89.0f, 78.0f))),
            deco = FigureDeco.None,
            scale = 0.9f,
        ),
        "Lunges" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(70.0f, 34.0f), Pt(77.0f, 44.0f)), listOf(Pt(82.0f, 66.0f), Pt(84.0f, 86.0f), Pt(93.0f, 86.0f)), leg2 = listOf(Pt(78.0f, 66.0f), Pt(76.0f, 86.0f), Pt(85.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 38.0f), Pt(80.0f, 62.0f), listOf(Pt(70.0f, 50.0f), Pt(77.0f, 60.0f)), listOf(Pt(99.0f, 64.0f), Pt(100.0f, 86.0f), Pt(109.0f, 86.0f)), leg2 = listOf(Pt(66.0f, 78.0f), Pt(50.0f, 84.0f), Pt(44.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "StepUp" to FigureSpec(
            a = FigurePose(Pt(60.0f, 22.0f), Pt(60.0f, 46.0f), listOf(Pt(50.0f, 34.0f), Pt(58.0f, 44.0f)), listOf(Pt(80.0f, 52.0f), Pt(94.0f, 66.0f), Pt(102.0f, 68.0f)), leg2 = listOf(Pt(60.0f, 66.0f), Pt(60.0f, 86.0f), Pt(69.0f, 86.0f))),
            b = FigurePose(Pt(98.0f, 2.0f), Pt(98.0f, 26.0f), listOf(Pt(88.0f, 14.0f), Pt(96.0f, 24.0f)), listOf(Pt(98.0f, 46.0f), Pt(98.0f, 66.0f), Pt(107.0f, 68.0f)), leg2 = listOf(Pt(112.0f, 38.0f), Pt(100.0f, 52.0f), Pt(96.0f, 54.0f))),
            deco = FigureDeco.Step,
            scale = 0.8f,
        ),
        "Bridge" to FigureSpec(
            a = FigurePose(Pt(40.0f, 84.0f), Pt(64.0f, 84.0f), listOf(Pt(52.0f, 85.0f), Pt(64.0f, 85.0f)), listOf(Pt(77.0f, 69.0f), Pt(90.0f, 86.0f), Pt(98.0f, 86.0f))),
            b = FigurePose(Pt(44.0f, 83.0f), Pt(64.0f, 68.0f), listOf(Pt(56.0f, 84.0f), Pt(68.0f, 85.0f)), listOf(Pt(83.0f, 66.0f), Pt(90.0f, 86.0f), Pt(98.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "CalfRaise" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(70.0f, 34.0f), Pt(77.0f, 44.0f)), listOf(Pt(80.0f, 66.0f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 16.0f), Pt(80.0f, 40.0f), listOf(Pt(70.0f, 28.0f), Pt(77.0f, 38.0f)), listOf(Pt(80.0f, 60.0f), Pt(81.0f, 80.0f), Pt(90.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "WallSit" to FigureSpec(
            a = FigurePose(Pt(58.0f, 22.0f), Pt(58.0f, 46.0f), listOf(Pt(66.0f, 34.0f), Pt(70.0f, 46.0f)), listOf(Pt(60.0f, 66.0f), Pt(60.0f, 86.0f), Pt(69.0f, 86.0f))),
            b = FigurePose(Pt(58.0f, 42.0f), Pt(58.0f, 66.0f), listOf(Pt(66.0f, 52.0f), Pt(74.0f, 62.0f)), listOf(Pt(78.0f, 66.0f), Pt(78.0f, 86.0f), Pt(87.0f, 86.0f))),
            deco = FigureDeco.WallLeft,
            scale = 1f,
        ),
        "DonkeyKick" to FigureSpec(
            a = FigurePose(Pt(100.0f, 64.0f), Pt(76.0f, 64.0f), listOf(Pt(100.0f, 76.0f), Pt(100.0f, 88.0f)), listOf(Pt(76.0f, 85.0f), Pt(56.0f, 86.0f), Pt(52.0f, 87.0f)), leg2 = listOf(Pt(79.0f, 85.0f), Pt(59.0f, 86.0f), Pt(55.0f, 87.0f))),
            b = FigurePose(Pt(100.0f, 64.0f), Pt(76.0f, 64.0f), listOf(Pt(100.0f, 76.0f), Pt(100.0f, 88.0f)), listOf(Pt(57.0f, 67.0f), Pt(59.0f, 48.0f), Pt(54.0f, 46.0f)), leg2 = listOf(Pt(79.0f, 85.0f), Pt(59.0f, 86.0f), Pt(55.0f, 87.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Crunches" to FigureSpec(
            a = FigurePose(Pt(46.0f, 84.0f), Pt(70.0f, 84.0f), listOf(Pt(47.0f, 73.0f), Pt(40.0f, 81.0f)), listOf(Pt(84.0f, 68.0f), Pt(98.0f, 84.0f), Pt(106.0f, 86.0f))),
            b = FigurePose(Pt(56.0f, 66.0f), Pt(70.0f, 84.0f), listOf(Pt(62.0f, 54.0f), Pt(53.0f, 60.0f)), listOf(Pt(84.0f, 68.0f), Pt(98.0f, 84.0f), Pt(106.0f, 86.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Bicycle" to FigureSpec(
            a = FigurePose(Pt(54.0f, 68.0f), Pt(70.0f, 84.0f), listOf(Pt(60.0f, 56.0f), Pt(51.0f, 61.0f)), listOf(Pt(84.0f, 68.0f), Pt(96.0f, 78.0f), Pt(102.0f, 80.0f)), leg2 = listOf(Pt(90.0f, 80.0f), Pt(110.0f, 76.0f), Pt(116.0f, 75.0f))),
            b = FigurePose(Pt(55.0f, 68.0f), Pt(70.0f, 84.0f), listOf(Pt(57.0f, 58.0f), Pt(51.0f, 62.0f)), listOf(Pt(90.0f, 80.0f), Pt(110.0f, 76.0f), Pt(116.0f, 75.0f)), leg2 = listOf(Pt(84.0f, 68.0f), Pt(96.0f, 78.0f), Pt(102.0f, 80.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "LegRaise" to FigureSpec(
            a = FigurePose(Pt(46.0f, 84.0f), Pt(70.0f, 84.0f), listOf(Pt(52.0f, 85.0f), Pt(64.0f, 85.0f)), listOf(Pt(90.0f, 83.0f), Pt(110.0f, 82.0f), Pt(116.0f, 81.0f))),
            b = FigurePose(Pt(46.0f, 84.0f), Pt(70.0f, 84.0f), listOf(Pt(52.0f, 85.0f), Pt(64.0f, 85.0f)), listOf(Pt(71.0f, 64.0f), Pt(72.0f, 44.0f), Pt(78.0f, 43.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Flutter" to FigureSpec(
            a = FigurePose(Pt(46.0f, 84.0f), Pt(70.0f, 84.0f), listOf(Pt(52.0f, 85.0f), Pt(64.0f, 85.0f)), listOf(Pt(88.0f, 76.0f), Pt(106.0f, 68.0f), Pt(112.0f, 66.0f)), leg2 = listOf(Pt(89.0f, 82.0f), Pt(108.0f, 80.0f), Pt(114.0f, 80.0f))),
            b = FigurePose(Pt(46.0f, 84.0f), Pt(70.0f, 84.0f), listOf(Pt(52.0f, 85.0f), Pt(64.0f, 85.0f)), listOf(Pt(89.0f, 82.0f), Pt(108.0f, 80.0f), Pt(114.0f, 80.0f)), leg2 = listOf(Pt(88.0f, 76.0f), Pt(106.0f, 68.0f), Pt(112.0f, 66.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Plank" to FigureSpec(
            a = FigurePose(Pt(100.0f, 62.0f), Pt(75.7f, 70.4f), listOf(Pt(100.0f, 84.0f), Pt(116.0f, 86.0f)), listOf(Pt(55.2f, 77.4f), Pt(36.0f, 84.0f), Pt(31.0f, 87.0f))),
            b = null,
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "SidePlank" to FigureSpec(
            a = FigurePose(Pt(106.0f, 62.0f), Pt(79.0f, 76.0f), listOf(Pt(106.0f, 84.0f), Pt(120.0f, 86.0f)), listOf(Pt(57.0f, 82.0f), Pt(36.0f, 86.0f), Pt(31.0f, 87.0f)), arm2 = listOf(Pt(106.0f, 50.0f), Pt(106.0f, 38.0f))),
            b = FigurePose(Pt(106.0f, 62.0f), Pt(79.0f, 69.0f), listOf(Pt(106.0f, 84.0f), Pt(120.0f, 86.0f)), listOf(Pt(57.0f, 78.0f), Pt(36.0f, 86.0f), Pt(31.0f, 87.0f)), arm2 = listOf(Pt(106.0f, 50.0f), Pt(106.0f, 38.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "BirdDog" to FigureSpec(
            a = FigurePose(Pt(100.0f, 64.0f), Pt(76.0f, 64.0f), listOf(Pt(100.0f, 76.0f), Pt(100.0f, 88.0f)), listOf(Pt(76.0f, 85.0f), Pt(56.0f, 86.0f), Pt(52.0f, 87.0f)), arm2 = listOf(Pt(103.0f, 76.0f), Pt(103.0f, 88.0f)), leg2 = listOf(Pt(79.0f, 85.0f), Pt(59.0f, 86.0f), Pt(55.0f, 87.0f))),
            b = FigurePose(Pt(100.0f, 64.0f), Pt(76.0f, 64.0f), listOf(Pt(100.0f, 76.0f), Pt(100.0f, 88.0f)), listOf(Pt(56.0f, 64.0f), Pt(36.0f, 63.0f), Pt(31.0f, 64.0f)), arm2 = listOf(Pt(112.0f, 64.0f), Pt(124.0f, 63.0f)), leg2 = listOf(Pt(79.0f, 85.0f), Pt(59.0f, 86.0f), Pt(55.0f, 87.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Burpee" to FigureSpec(
            a = FigurePose(Pt(92.0f, 64.0f), Pt(72.0f, 70.0f), listOf(Pt(96.0f, 75.0f), Pt(100.0f, 86.0f)), listOf(Pt(90.0f, 72.0f), Pt(78.0f, 86.0f), Pt(87.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 24.0f), Pt(80.0f, 48.0f), listOf(Pt(86.0f, 14.0f), Pt(88.0f, 3.0f)), listOf(Pt(80.0f, 67.0f), Pt(80.0f, 84.0f), Pt(89.0f, 84.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Jacks" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(76.0f, 34.0f), Pt(74.0f, 46.0f)), listOf(Pt(77.0f, 66.0f), Pt(77.0f, 86.0f), Pt(73.0f, 86.0f)), arm2 = listOf(Pt(84.0f, 34.0f), Pt(86.0f, 46.0f)), leg2 = listOf(Pt(83.0f, 66.0f), Pt(83.0f, 86.0f), Pt(87.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(70.0f, 16.0f), Pt(62.0f, 6.0f)), listOf(Pt(68.0f, 64.0f), Pt(58.0f, 82.0f), Pt(53.0f, 83.0f)), arm2 = listOf(Pt(90.0f, 16.0f), Pt(98.0f, 6.0f)), leg2 = listOf(Pt(92.0f, 64.0f), Pt(102.0f, 82.0f), Pt(107.0f, 83.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "Mountain" to FigureSpec(
            a = FigurePose(Pt(108.0f, 64.0f), Pt(80.6f, 72.4f), listOf(Pt(108.0f, 76.0f), Pt(108.0f, 88.0f)), listOf(Pt(57.6f, 79.4f), Pt(36.0f, 86.0f), Pt(31.0f, 88.0f)), leg2 = listOf(Pt(98.0f, 76.0f), Pt(86.0f, 84.0f), Pt(82.0f, 86.0f))),
            b = FigurePose(Pt(108.0f, 64.0f), Pt(80.6f, 72.4f), listOf(Pt(108.0f, 76.0f), Pt(108.0f, 88.0f)), listOf(Pt(98.0f, 76.0f), Pt(86.0f, 84.0f), Pt(82.0f, 86.0f)), leg2 = listOf(Pt(57.6f, 79.4f), Pt(36.0f, 86.0f), Pt(31.0f, 88.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
        "HighKnees" to FigureSpec(
            a = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(74.0f, 33.0f), Pt(86.0f, 38.0f)), listOf(Pt(99.0f, 48.0f), Pt(98.0f, 67.0f), Pt(106.0f, 67.0f)), arm2 = listOf(Pt(86.0f, 33.0f), Pt(74.0f, 38.0f)), leg2 = listOf(Pt(80.0f, 66.0f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f))),
            b = FigurePose(Pt(80.0f, 22.0f), Pt(80.0f, 46.0f), listOf(Pt(86.0f, 33.0f), Pt(74.0f, 38.0f)), listOf(Pt(80.0f, 66.0f), Pt(80.0f, 86.0f), Pt(89.0f, 86.0f)), arm2 = listOf(Pt(74.0f, 33.0f), Pt(86.0f, 38.0f)), leg2 = listOf(Pt(99.0f, 48.0f), Pt(98.0f, 67.0f), Pt(106.0f, 67.0f))),
            deco = FigureDeco.None,
            scale = 1f,
        ),
    )
}
