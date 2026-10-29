package com.spe.calculator.service;

import com.spe.calculator.exception.DivisionByZeroException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorServiceTest {

    private static final double DELTA = 1e-9;

    private final CalculatorService service = new CalculatorService();

    @ParameterizedTest
    @CsvSource({"2, 3, 5", "-4, 1, -3", "0, 0, 0", "1.5, 2.25, 3.75"})
    void addReturnsSum(double a, double b, double expected) {
        assertEquals(expected, service.add(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"5, 3, 2", "3, 5, -2", "-2, -2, 0", "10.5, 0.5, 10"})
    void subtractReturnsDifference(double a, double b, double expected) {
        assertEquals(expected, service.subtract(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"2, 3, 6", "-2, 3, -6", "-2, -3, 6", "7, 0, 0", "0.5, 0.5, 0.25"})
    void multiplyReturnsProduct(double a, double b, double expected) {
        assertEquals(expected, service.multiply(a, b), DELTA);
    }

    @ParameterizedTest
    @CsvSource({"6, 3, 2", "7, 2, 3.5", "-9, 3, -3", "0, 5, 0", "1, 4, 0.25"})
    void divideReturnsQuotient(double a, double b, double expected) {
        assertEquals(expected, service.divide(a, b), DELTA);
    }

    @Test
    void divideByZeroThrowsException() {
        DivisionByZeroException ex = assertThrows(DivisionByZeroException.class, () -> service.divide(10, 0));
        assertEquals("Division by zero is not allowed", ex.getMessage());
    }

    @Test
    void divideByNegativeZeroThrowsException() {
        assertThrows(DivisionByZeroException.class, () -> service.divide(10, -0.0));
    }
}
