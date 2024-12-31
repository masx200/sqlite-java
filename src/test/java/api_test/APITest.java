package api_test;

import com.github.masx200.sqlite_java.Column;
import com.github.masx200.sqlite_java.DB;
import com.github.masx200.sqlite_java.DataSupport;
import com.github.masx200.sqlite_java.Options;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class APITest {

    public static class User extends DataSupport<User> {
        @Column(index = true)
        public Long uid;
        public String name;
        public Integer age;
        public Boolean vip;
        @Column(json = true)
        public List<String> labels;

        public User(Consumer<User> consumer) {
            super(consumer);
        }
    }

    public static class Book extends DataSupport<Book> {
        public String name;
        public String author;
        public Double price;

        public Book(Consumer<Book> consumer) {
            super(consumer);
        }
    }

    DB connect() {
        DB db = DB.connect("database/example.db");
        db.tables(User.class);
        // db.deleteAll(User.class);
        return db;
    }

    @Test
    void insert() {
        List<User> users = Arrays.asList(new User(u -> {
            u.name = "user1";
            u.age = 18;
            u.vip = false;
        }), new User(u -> {
            u.name = "user2";
            u.age = 20;
            u.vip = true;
        }), new User(u -> {
            u.name = "user3";
            u.age = 22;
            u.vip = false;
        }), new User(u -> {
            u.name = "user4";
            u.age = 24;
            u.vip = true;
        }), new User(u -> {
            u.name = "user5";
            u.age = 26;
            u.vip = true;
        }));

        try (DB db = connect()) {
            users.forEach(u -> System.out.println(db.insert(u)));
        }
    }

    @Test
    void updateById() {
        try (DB db = connect()) {
            insert();
            User user = db.first(User.class);
            System.out.println(   db.updateById(user.set(u -> u.age = 60)));
        }
    }

    @Test
    void updateByCondition() {
        DB db = connect();
        insert();
        db.updateByPredicate(new User(u -> u.age = 70), "name = ?", "user4");
    }

    @Test
    void findOneById() {
        User user;
        try (DB db = connect()) {
            insert();
            var id = db.first(User.class).id();
            user = db.findOneById(User.class, id);
        }
        user.printJson();
    }

    @Test
    void findOneByCondition() {
        User user;
        try (DB db = connect()) {
            insert();
            user = db.findOneByPredicate(User.class, "name = ?", "user3");
        }
        user.printJson();
    }

    @Test
    void findAll() {
        List<User> users;
        try (DB db = connect()) {
            insert();
            users = db.findAll(User.class);
        }
        users.forEach(DataSupport::printJson);
    }

    @Test
    void findByIds() {
        List<User> users;
        try (DB db = connect()) {
            insert();
            users = db.findByVarargId(User.class, 2L, 1L);
        }
        users.forEach(DataSupport::printJson);
    }

    @Test
    void findByIdList() {
        List<User> users;
        try (DB db = connect()) {
            insert();
            users = db.findByListId(User.class, Arrays.asList(1L, 2L));
        }
        users.forEach(DataSupport::printJson);
    }

    @Test
    void find() {
        List<User> users;
        try (DB db = connect()) {
            insert();
            users = db.findByConsumer(User.class, options -> options
                    .select("name", "age")
                    .where("age <= ? && vip = ?", 50, true)
                    .order("age", Options.DESC)
                    .limit(5)
                    .offset(0));
        }
        users.forEach(DataSupport::printJson);
    }

    @Test
    void deleteAll() {
        try (DB db = connect()) {
            insert();
            System.out.println( db.deleteAll(User.class));   ;
        }
    }

    @Test
    void deleteByIdList() {
        try (DB db = connect()) {
            System.out.println(   db.deleteByListId(User.class, Arrays.asList(1L, 4L)));
        }
    }

    @Test
    void deleteByCondition() {
        try (DB db = connect()) {
            insert();
            System.out.println(    db.deleteByPredicate(User.class, "name = ?", "user3"));
        }
    }

    @Test
    void deleteByIds() {
        try (DB db = connect()) {
            insert();
            System.out.println(db.deleteByVarargId(User.class, 4L));
        } catch (Exception e) {
//            e.printStackTrace();
            throw e;
        }

    }

    @Test
    void first() {
        User user2;
        try (DB db = connect()) {
            insert();

            User user1 = db.first(User.class);
            user1.printJson();

            user2 = db.firstByPredicate(User.class, "vip = ?", true);
        }
        user2.printJson();
    }

    @Test
    void last() {
        User user2;
        try (DB db = connect()) {
            insert();

            User user1 = db.last(User.class);
            user1.printJson();

            user2 = db.lastByPredicate(User.class, "vip = ?", false);
        }
        user2.printJson();
    }

    @Test
    void count() {
        long count2;
        try (DB db = connect()) {
            insert();

            long count1 = db.count(User.class);
            System.out.println(count1);

            count2 = db.countByPredicate(User.class, "vip = ?", true);
        }
        System.out.println(count2);
    }

    @Test
    void average() {
        double d2;
        try (DB db = connect()) {
            insert();

            double d1 = db.average(User.class, "age");
            System.out.println(d1);

            d2 = db.averageByPredicate(User.class, "age", "vip = ?", false);
        }
        System.out.println(d2);
    }

    @Test
    void sum() {
        int i2;
        try (DB db = connect()) {
            insert();

            int i1 = db.sum(User.class, "age").intValue();
            System.out.println(i1);

            i2 = db.sumByPredicate(User.class, "age", "vip = ?", false).intValue();
        }
        System.out.println(i2);
    }

    @Test
    void max() {
        int age2;
        try (DB db = connect()) {
            insert();

            int age1 = db.max(User.class, "age").intValue();
            System.out.println(age1);

            age2 = db.maxByPredicate(User.class, "age", "vip = ?", false).intValue();
        }
        System.out.println(age2);
    }

    @Test
    void min() {
        int age2;
        try (DB db = connect()) {
            insert();

            int age1 = db.min(User.class, "age").intValue();
            System.out.println(age1);

            age2 = db.minByPredicate(User.class, "age", "vip = ?", true).intValue();
        }
        System.out.println(age2);
    }

    @Test
    void version() {
        String version;
        try (DB db = connect()) {
            version = db.version();
        }
        System.out.println(version);
    }

}