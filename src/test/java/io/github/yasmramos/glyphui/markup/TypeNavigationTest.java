package io.github.yasmramos.glyphui.markup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests declared member navigation and supertype resolution without access
 * reflection.
 */
class TypeNavigationTest {

    private static class Person {
        private final String name;
        private final int age;

        Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    private static final class Employee extends Person {
        private final String company;

        Employee(String name, int age, String company) {
            super(name, age);
            this.company = company;
        }
    }

    private interface Named {
        String label();
    }

    private static final class NamedThing implements Named {
        private final String label;

        NamedThing(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    @Test
    void fluentRegistrationReadsDeclaredMembers() {
        TypeNavigations navigations = new TypeNavigations();

        navigations.register(Person.class)
                .member("name", person -> person.name)
                .member("age", person -> person.age)
                .done();

        TypeNavigation<Person> nav = navigations.get(Person.class);
        Person person = new Person("Ada", 36);

        assertTrue(nav.hasMember("name"));
        assertFalse(nav.hasMember("missing"));
        assertEquals("Ada", nav.get(person, "name"));
        assertEquals(36, nav.get(person, "age"));
        assertEquals(java.util.Set.of("name", "age"), nav.members());
    }

    @Test
    void registeringTheSameTypeAgainKeepsTheExistingNavigation() {
        TypeNavigations navigations = new TypeNavigations();

        TypeNavigation<Person> first = navigations.register(Person.class);
        first.member("name", person -> person.name);
        TypeNavigation<Person> second = navigations.register(Person.class);

        assertSame(first, second);
        second.member("age", person -> person.age);
        assertEquals(2, navigations.get(Person.class).members().size());
    }

    @Test
    void registeredSuperclassesResolveForSubtypes() {
        TypeNavigations navigations = new TypeNavigations();
        navigations.register(Person.class)
                .member("name", person -> person.name)
                .done();

        assertTrue(navigations.knows(Employee.class));

        TypeNavigation<Employee> nav = navigations.get(Employee.class);
        Employee employee = new Employee("Grace", 44, "Navy");
        assertEquals(Person.class, nav.getType());
        assertEquals("Grace", nav.get(employee, "name"));
    }

    @Test
    void registeredInterfacesResolveForImplementations() {
        TypeNavigations navigations = new TypeNavigations();
        navigations.register(Named.class)
                .member("label", Named::label)
                .done();

        assertTrue(navigations.knows(NamedThing.class));
        TypeNavigation<NamedThing> nav = navigations.get(NamedThing.class);
        assertEquals("hello", nav.get(new NamedThing("hello"), "label"));
    }

    @Test
    void exactRegistrationWinsOverSupertype() {
        TypeNavigations navigations = new TypeNavigations();
        navigations.register(Person.class)
                .member("name", person -> person.name)
                .done();
        navigations.register(Employee.class)
                .member("company", employee -> employee.company)
                .done();

        TypeNavigation<Employee> nav = navigations.get(Employee.class);
        assertEquals(Employee.class, nav.getType());
        assertTrue(nav.hasMember("company"));
        assertFalse(nav.hasMember("name"));
    }

    @Test
    void unknownMembersAndTypesReportClearErrors() {
        TypeNavigations navigations = new TypeNavigations();
        navigations.register(Person.class)
                .member("name", person -> person.name)
                .done();

        IllegalArgumentException memberError = assertThrows(IllegalArgumentException.class,
                () -> navigations.get(Person.class).get(new Person("Ada", 36), "salary"));
        assertTrue(memberError.getMessage().contains("salary"), memberError.getMessage());
        assertTrue(memberError.getMessage().contains("name"), memberError.getMessage());

        IllegalArgumentException typeError = assertThrows(IllegalArgumentException.class,
                () -> navigations.get(String.class));
        assertTrue(typeError.getMessage().contains(String.class.getName()),
                typeError.getMessage());

        assertFalse(navigations.knows(String.class));
        assertFalse(navigations.knows(null));
    }

    @Test
    void invalidDeclarationsAreRejected() {
        TypeNavigations navigations = new TypeNavigations();

        assertThrows(IllegalArgumentException.class, () -> navigations.register(null));
        assertThrows(IllegalArgumentException.class, () -> navigations.get((Class<Object>) null));
        assertThrows(IllegalArgumentException.class,
                () -> navigations.register(Person.class).member(" ", person -> person.name));
        assertThrows(IllegalArgumentException.class,
                () -> navigations.register(Person.class).member("name", null));
        assertThrows(IllegalArgumentException.class,
                () -> navigations.register(Person.class).member("name", p -> p.name)
                        .get(null, "name"));
    }
}
