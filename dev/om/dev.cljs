(ns om.dev
  (:require-macros [om.dev :refer [defui]])
  (:require [goog.string :as gstring]
            [clojure.browser.repl :as repl]
            [om.core :as om]
            [om.dom :as dom]
            [clojure.walk :as walk]))

(defonce conn
  (repl/connect "http://localhost:9000/repl"))

(defprotocol IQueryParams
  (params [this]))

(defprotocol IQuery
  (queries [this]))

(defprotocol IQueryEngine
  (-run-query [this db q]))

(defprotocol IStorage
  (-transact [this db xs]))

(defn run-query [x db q]
  (-run-query x db q))

(defn var? [x]
  (and (symbol? x)
       (gstring/startsWith (name x) "?")))

(defn var->keyword [x]
  (keyword (.substring (name x) 1)))

(defn bind-query [query params]
  (letfn [(replace-var [node]
            (if (var? node)
              (get params (var->keyword node) node)
              node))]
    (walk/prewalk replace-var query)))

(defn get-query
  ([cl] (get-query cl :self))
  ([cl k]
   (with-meta
     (bind-query (k (queries cl)) (k (params cl)))
     {:class cl})))

(defn pull [x selector])

(defn complete-query [cl]
  (letfn [(bind [k]
            (bind-query (k (queries cl)) (k (params cl))))
          (key-repeat [k]
            (repeat (count (k (queries cl))) k))
          (key-order [ks]
            (vec (mapcat key-repeat ks)))]
    (let [qs (queries cl)
          qks (keys qs)
          bound-qs (map bind qks)]
      (with-meta
        (reduce into (first bound-qs) (rest bound-qs))
        {:class cl :key-order (key-order qks)}))))

(deftype TreeQuery [foreign-keys]
  IQueryEngine
  (-run-query [this db q]
    ))

(deftype TreeStorage []
  IStorage
  (-transact [this db xs]
    ))

(comment
  ;; db at top-level, entities
  (def db
    {:albums  {0 {:album/name "Awesome Album"
                  :album/tracks [0]}}
     :tracks  {0 {:track/name "Awesome Track"
                  :track/artists [0]}}
     :artists {0 {:db/id 0 :artist/name "Bob Smith"}}})

  (def fks
    {:album/tracks  :tracks
     :track/artists :artists})

  (bind-query '[:foo (?bar)] {:bar 3})

  (defui Artist
    static field sel '[:db/id :artist/name]
    Object
    (render [this]))

  (defui Track
    static IQueryParams
    (params [this]
      {:artists {:artist Artist.sel}})
    static IQuery
    (queries [this]
      '{:self [:db/id :track/name]
        :artists [{:track/artists ?artist}]})
    Object
    (render [this]))

  (defui AlbumTracks
    static IQueryParams
    (params [this]
      {:self {:tracks (complete-query Track)}})
    static IQuery
    (queries [this]
      '{:self [:album/name {:album/tracks ?tracks}]}))

  (.render (Track. nil nil nil))

  (get-query Track)
  (get-query Track :artists)
  (get-query AlbumTracks)

  (complete-query AlbumTracks)
  (-> (complete-query AlbumTracks) meta)
  (-> (complete-query AlbumTracks) second meta)

  )