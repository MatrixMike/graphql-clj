(ns graphql-clj.executor-test
  (:use graphql-clj.executor)
  (:require [clojure.test :refer :all]
            [graphql-clj.type :as type]
            [graphql-clj.parser :as parser]
            [graphql-clj.resolver :as resolver]
            [clojure.core.match :as match]))

(def simple-user-schema
  "type User {
  name: String
  nickname: String
  son: User
  friends: [User]
}

type QueryRoot {
  user: User
}

schema {
  query: QueryRoot
}")

(def customized-resolver-fn
  (fn [type-name field-name]
    (match/match
     [type-name field-name]
     ["QueryRoot"  "user"] (fn [& args]
                             {:name "Test user name"
                              :nickname "Test user nickname"})
     ["User" "son"] (fn [context parent & args]
                      {:name "Test son name"
                       :nickname "Son's nickname"})
     ["User" "friends"] (fn [context parent & args]
                          (map (fn [no] {:name "Friend 1 name"
                                        :nickname "Friend 1 nickname"})
                               (range 5)))
     :else nil)))

(defn- create-test-schema
  [type-spec]
  (-> type-spec
      (parser/parse)
      (type/create-schema)))

(deftest test-simple-execution
  (testing "test simple execution"
    (let [schema (create-test-schema simple-user-schema)
          query "query {user {name}}"
          document (parser/parse query)
          context nil
          query-operation (first (:operation-definitions document))
          query-selection-set (:selection-set query-operation)
          user-selection (first query-selection-set)
          user-selection-set (get-in (second user-selection) [:field :selection-set])
          new-document (parser/parse "query {user {name son}}")
          resolver-fn (resolver/create-resolver-fn schema customized-resolver-fn)
          new-result (execute context schema resolver-fn new-document)
          ]
      (is (= "user" (get-selection-name user-selection)))
      (is (= :field (get-selection-type user-selection)))
      (is (= user-selection-set (get-field-selection-set user-selection)))
      (is (= [[:selection {:field {:name "name"}}]] (collect-fields user-selection-set nil)))
      (is (= "Test user name" (get-in (execute context schema resolver-fn document) [:data "user" "name"])))
      )))

(deftest test-execution-on-list
  (testing "test execution on list"
    (let [schema (create-test-schema simple-user-schema)
          query "query {user {name friends{name}}}"
          document (parser/parse query)
          resolver-fn (resolver/create-resolver-fn schema customized-resolver-fn)
          context nil]
      (is (= 5 (count (get-in (execute context schema resolver-fn document)
                              [:data "user" "friends"])))))))

(deftest test-execution-with-fragment
  (testing "test execution with fragment"
    (let [schema (create-test-schema simple-user-schema)
          query "query {user {...userFields friends{...userFields}}}
fragment userFields on User {
  name
  nickname
}"
          document (parser/parse query)
          resolver-fn (resolver/create-resolver-fn schema customized-resolver-fn)
          context nil]
      (is (= 5 (count (get-in (execute context schema resolver-fn document)
                              [:data "user" "friends"])))))))
