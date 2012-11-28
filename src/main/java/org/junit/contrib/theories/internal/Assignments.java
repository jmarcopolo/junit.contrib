package org.junit.contrib.theories.internal;

import com.thoughtworks.paranamer.Paranamer;
import org.junit.contrib.theories.ParameterSignature;
import org.junit.contrib.theories.ParameterSupplier;
import org.junit.contrib.theories.ParametersSuppliedBy;
import org.junit.contrib.theories.PotentialAssignment;
import org.junit.contrib.theories.PotentialAssignment.CouldNotGenerateValueException;
import org.junit.runners.model.TestClass;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A potentially incomplete list of value assignments for a method's formal parameters
 */
public class Assignments {
    private List<PotentialAssignment> fAssigned;
    private final List<ParameterSignature> fUnassigned;
    private final TestClass fClass;
    private final Paranamer fParanamer;

    private Assignments(List<PotentialAssignment> assigned, List<ParameterSignature> unassigned, TestClass testClass,
            Paranamer paranamer) {
        fUnassigned = unassigned;
        fAssigned = assigned;
        fClass = testClass;
        fParanamer = paranamer;
    }

    /**
     * Returns a new assignment list for {@code testMethod}, with no params assigned.
     */
    public static Assignments allUnassigned(Method testMethod, TestClass testClass, Paranamer paranamer)
            throws Exception {
        List<ParameterSignature> signatures =
                ParameterSignature.signatures(testClass.getOnlyConstructor(), paranamer);
        signatures.addAll(ParameterSignature.signatures(testMethod, paranamer));
        return new Assignments(new ArrayList<PotentialAssignment>(), signatures, testClass, paranamer);
    }

    public boolean isComplete() {
        return fUnassigned.size() == 0;
    }

    public ParameterSignature nextUnassigned() {
        return fUnassigned.get(0);
    }

    public Assignments assignNext(PotentialAssignment source) {
        List<PotentialAssignment> assigned = new ArrayList<PotentialAssignment>(fAssigned);
        assigned.add(source);

        return new Assignments(assigned, fUnassigned.subList(1, fUnassigned.size()), fClass, fParanamer);
    }

    public Object[] getActualValues(int start, int stop, boolean nullsOk) throws CouldNotGenerateValueException {
        Object[] values = new Object[stop - start];
        for (int i = start; i < stop; i++) {
            Object value = fAssigned.get(i).getValue();
            if (value == null && !nullsOk) {
                throw new CouldNotGenerateValueException();
            }
            values[i - start] = value;
        }
        return values;
    }

    public List<PotentialAssignment> potentialsForNextUnassigned()
            throws InstantiationException, IllegalAccessException {

        ParameterSignature unassigned = nextUnassigned();
        return getSupplier(unassigned).getValueSources(unassigned);
    }

    public ParameterSupplier getSupplier(ParameterSignature unassigned)
            throws InstantiationException, IllegalAccessException {

        ParameterSupplier supplier = getAnnotatedSupplier(unassigned);
        if (supplier != null) {
            return supplier;
        }

        return new AllMembersSupplier(fClass);
    }

    public ParameterSupplier getAnnotatedSupplier(ParameterSignature unassigned)
            throws InstantiationException, IllegalAccessException {

        ParametersSuppliedBy annotation = unassigned.findDeepAnnotation(ParametersSuppliedBy.class);
        if (annotation == null) {
            return null;
        }
        return annotation.value().newInstance();
    }

    public Object[] getConstructorArguments(boolean nullsOk) throws CouldNotGenerateValueException {
        return getActualValues(0, getConstructorParameterCount(), nullsOk);
    }

    public Object[] getMethodArguments(boolean nullsOk) throws CouldNotGenerateValueException {
        return getActualValues(getConstructorParameterCount(), fAssigned.size(), nullsOk);
    }

    private int getConstructorParameterCount() {
        List<ParameterSignature> signatures = ParameterSignature.signatures(fClass.getOnlyConstructor(), fParanamer);
        return signatures.size();
    }

    public Object[] getArgumentStrings(boolean nullsOk) throws CouldNotGenerateValueException {
        Object[] values = new Object[fAssigned.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = fAssigned.get(i).getDescription();
        }
        return values;
    }
}
