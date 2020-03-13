package com.bgerstle.result;

import static java.lang.Integer.parseInt;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.Generate.oneOf;
import static org.quicktheories.generators.SourceDSL.strings;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quicktheories.core.Gen;

import com.bgerstle.result.Result;
import com.bgerstle.result.ResultCollector;

@SuppressWarnings({ "squid:S00112", "squid:S1452" })
public class ResultTests {

    private static final String EXAMPLE_URI = "http://example.com";

    @SuppressWarnings("squid:S1612")
    public static Gen<? extends Result<Object, Exception>> results() {
        Gen<String> strs = strings().allPossible().ofLengthBetween(1, 5);
        return oneOf(strs.map(Object.class::cast), strs.map(s -> new Exception(s)).map(Object.class::cast)).map(Result::of);
    }

    @Test
    void emptyOptionalTransformationExample() {
        assertThrows(NoSuchElementException.class, () -> Result.<String, Exception> attempt(Optional.<String> empty()::get).flatMapAttempt(URI::new).orElseThrow());
    }

    @Test
    void optionalValidURITransformationExample() throws URISyntaxException {
        URI apiBaseUrl = Result.<String, URISyntaxException> attempt(Optional.of(EXAMPLE_URI)::get).flatMapAttempt(URI::new).orElseThrow();
        assertThat(apiBaseUrl, equalTo(new URI(EXAMPLE_URI)));
    }

    @Test
    void wrapsValues() {
        assertThat(Result.attempt(() -> "foo").orElseAssert(), is("foo"));
    }

    @ParameterizedTest
    @ValueSource(classes = { RuntimeException.class, IOException.class, Exception.class })
    void wrapsExceptions(Class<Exception> exceptionClass) {
        assertThrows(exceptionClass, () -> Result.attempt(() -> {
            throw exceptionClass.getConstructor().newInstance();
        }).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(classes = { RuntimeException.class, IOException.class, Exception.class })
    void recoversFromExceptions(Class<Exception> exceptionClass) throws Exception {
        assertThat(Result.failure(exceptionClass.getConstructor().newInstance()).recover(exceptionClass, () -> "foo").orElseThrow(), equalTo("foo"));
    }

    @ParameterizedTest
    @ValueSource(classes = { RuntimeException.class, IOException.class, Exception.class })
    void recoverNoopsForOtherExceptions(Class<Exception> exceptionClass) {
        assertThrows(exceptionClass, () -> Result.failure(exceptionClass.getConstructor().newInstance()).recover(TimeoutException.class, () -> "not supposed to be called").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(classes = { RuntimeException.class, IllegalArgumentException.class, IllegalStateException.class })
    void recoversClassAndItsSubclasses(Class<Exception> exceptionClass) throws Exception {
        assertThat(Result.failure(exceptionClass.getConstructor().newInstance()).recover(RuntimeException.class, () -> "foo").orElseThrow(), equalTo("foo"));
    }

    @Test
    void wrapsSubtypeExceptions() {
        assertThrows(RuntimeException.class, () -> Result.<Object, Exception> attempt(() -> {
            throw new RuntimeException();
        }).orElseThrow());
    }

    @Test
    void flatmapsNewValue() {
        qt().forAll(strings().allPossible().ofLengthBetween(1, 10), strings().allPossible().ofLengthBetween(1, 10))
                .checkAssert((s1, s2) -> assertThat(Result.attempt(() -> s1).flatMap(f -> Result.success(f + s2)).orElseAssert(), is(s1 + s2)));
    }

    @Test
    void flatmapsNewError() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> Result.success("foo").flatMapAttempt((f) -> f.charAt(f.length())).orElseThrow());
    }

    @Test
    void errorsSuppressSubsequentMaps() {
        assertThrows(StringIndexOutOfBoundsException.class, () -> Result.success("foo").flatMapAttempt((f) -> f.charAt(f.length())).map((c) -> {
            fail();
            return Character.toUpperCase(c);
        }).orElseThrow());
    }

    @Test
    void errorResultsAreEmpty() {
        assertThat(Result.failure(new Exception()).toOptional().isPresent(), is(false));
    }

    @Test
    void successResultsArePresent() {
        assertThat(Result.success(0).toOptional().isPresent(), is(true));
    }

    @Test
    void resultEquality() {
        qt().forAll(results(), results().toOptionals(50)).checkAssert((r1, r2) -> assertThat(
                // all result equality-based method results
                (r1.equals(r2.orElse(null)) && r1.toString().equals(r2.map(Result::toString).orElse(null)) && r1.hashCode() == r2.map(Result::hashCode).orElse(null)),
                // are identical to the equality of their underlying value/error
                is(r1.getEither().equals(r2.map(Result::getEither).orElse(null)))));
    }

    @Test
    void resultIdentity() {
        qt().forAll(results()).checkAssert(r -> assertThat((r.equals(r) && r.toString().equals(r.toString()) && r.hashCode() == r.hashCode()), is(true)));
    }

    @SuppressWarnings("squid:RedundantThrowsDeclarationCheck")
    List<Integer> parseInts(List<String> intStrings) throws NumberFormatException {
        return intStrings.stream().map(i -> Result.<Integer, NumberFormatException> attempt(() -> parseInt(i))).collect(new ResultCollector<>()).orElseThrow();
    }

    @Test
    void resultCollectorAllSuccessful() {
        assertThat(parseInts(List.of("1", "2", "3", "1337")), equalTo(List.of(1, 2, 3, 1337)));
    }

    @Test
    void resultCollectorThrowsOnFailure() {
        assertThrows(NumberFormatException.class, () -> parseInts(List.of("1", "2", "can't parse me")));
    }

    @Test
    void mapResultFromThrowingFunction() throws URISyntaxException {
        var uriString = EXAMPLE_URI;
        var uri = new URI(uriString);
        assertThat(Optional.ofNullable(uriString).map(Result.from(URI::new)).get().orElseThrow(), equalTo(uri));
    }

    @Test
    void resultFromThrowsError() {
        var uriString = "://";
        assertThrows(URISyntaxException.class, () -> Optional.ofNullable(uriString).map(Result.from(URI::new)).get().orElseThrow());
    }

    URI parseUri(String nullableString) throws URISyntaxException {
        return Result.<String, Exception> attempt(Optional.ofNullable(nullableString)::get).flatMap(Result.from(URI::new)).map(Optional::of).recover(NoSuchElementException.class, Optional::empty)
                .orElseThrowAs(e -> (URISyntaxException) e).orElse(null);
    }

    @Test
    void recoverFlatMapExampleSuccess() throws URISyntaxException {
        assertThat(parseUri(EXAMPLE_URI), equalTo(new URI(EXAMPLE_URI)));
    }

    @Test
    void recoverFlatMapExampleFailure() {
        assertThrows(URISyntaxException.class, () -> parseUri("://"));
    }

    @Test
    void recoverFlatMapExampleNull() throws URISyntaxException {
        assertThat(parseUri(null), nullValue());
    }
}
