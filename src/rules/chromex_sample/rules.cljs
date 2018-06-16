(ns chromex-sample.rules
  (:require-macros [chromex.support :refer [runonce]])
  (:require [chromex-sample.rules.core :as core]))


(runonce
  (core/init!))


