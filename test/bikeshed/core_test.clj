(ns bikeshed.core-test
  (:use clojure.test
        bikeshed.core))

(deftest a-test	
  (testing "FIXME, I fail, and I have trailing whitespace!"   
    (is (= 0 1))))

(deftest another-test
  (with-redefs [get get]
    (is (= 2 2))))

(def this-thing-is-over-eighty-characters-long "yep, it certainly is over eighty characters long")

(def a "≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡≡")

;; lot's of extra newlines:



















