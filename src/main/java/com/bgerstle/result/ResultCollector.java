package com.bgerstle.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class ResultCollector<V, E extends Exception> implements Collector<Result<V, E>, Result<List<V>, E>, Result<List<V>, E>> {

    private Result<List<V>, E> finalListResult;

    public ResultCollector() {
        finalListResult = Result.success(new ArrayList<>());
    }

    public static <V, E extends Exception> ResultCollector<V, E> of() {
        return new ResultCollector<>();
    }

    @Override
    public Supplier<Result<List<V>, E>> supplier() {
        return () -> finalListResult;
    }

    private void accumulate(Result<List<V>, E> unused, Result<V, E> result) {
        this.finalListResult = finalListResult.flatMap(values -> result.map(value -> {
            values.add(value);
            return values;
        }));
    }

    @Override
    public BiConsumer<Result<List<V>, E>, Result<V, E>> accumulator() {
        return this::accumulate;
    }

    @Override
    public BinaryOperator<Result<List<V>, E>> combiner() {
        return (rs1, rs2) -> {
            assert rs1 == rs2;
            assert rs2 == finalListResult;
            return finalListResult;
        };
    }

    @Override
    public Function<Result<List<V>, E>, Result<List<V>, E>> finisher() {
        return r -> finalListResult;
    }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of();
    }

}