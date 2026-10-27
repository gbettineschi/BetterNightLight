package io.github.paulsnuff.betternightlight.samsung;

interface ISamsungWorker {
    void destroy() = 16777114;
    int[] snapshot() = 1;
    void applyLevel(int level) = 2;
    void pause() = 3;
    void restore(in int[] values) = 4;
}
