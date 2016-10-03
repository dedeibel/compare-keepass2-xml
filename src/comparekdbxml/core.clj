(ns comparekdbxml.core
  (:require [clojure.set :as set]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip :as zf]
            [clojure.data.zip.xml :as zip-xml])
  (:use clojure.pprint))

(declare descent=)

(defn entries [xml-zipped]
  (zip-xml/xml-> xml-zipped
                 :KeePassFile
                 :Root
                 :Group 
                 (descent= :Group)
                 :Entry))

; xml-zip can't descent into a nested element using the
; :keyword locator since it tries to match the current element
; before going deeper. This locator looks in the child nodes only
(defn descent=
  [tagname]
  (fn [loc]
        (filter #(and (zip/branch? %) (= tagname (:tag (zip/node %))))
                (zf/children-auto loc))))

(defn entry-value [entry key]
  (zip-xml/xml1-> entry
                 :String
                 [:Key key]
                 :Value
                 zip-xml/text))

(defn parse-entry [xml-zipped]
  (for [entry (entries xml-zipped)]
    { :title (entry-value entry "Title")
      :notes (entry-value entry "Notes") 
      :url (entry-value entry "URL") 
      :username (entry-value entry "UserName") 
      :pw (entry-value entry "Password")}))

(defn xml-zipped [filepath]
  (-> filepath
      io/file
      xml/parse
      zip/xml-zip))

(defn titles [filepath]
  (apply sorted-set
         (map :title (parse-entry 
           (xml-zipped filepath)))))

(defn concat-values [entry]
  (apply str (vals entry)))

(defn compare-entry [a b]
  (.compareTo (concat-values a) (concat-values b)))

(defn entry-by-title [entry-a entry-b]
  (.compareTo (:title entry-a) (:title entry-b)))

(defn parsed-entries [filepath]
  (apply sorted-set-by entry-by-title
         (parse-entry 
           (xml-zipped filepath))))

(defn print-differences [file-a file-b]
  (let [entries-a (parsed-entries file-a)
        entries-b (parsed-entries file-b)
        additional-a (set/difference entries-a entries-b)
        additional-b (set/difference entries-b entries-a) ]
    (do
      (println "additional in" file-a "\n")
      (doseq [entry additional-a]
        (println entry))
; If you need more detail on single entries
;        (pprint entry))
      (println "\nadditional in" file-b "\n")
      (doseq [entry additional-b]
        (println entry))
;        (pprint entry))
    )))

(defn -main
  ([]
   (println "Usage: FILE-A FILE-B"))
  ([file-a file-b]
   (print-differences file-a file-b)))
