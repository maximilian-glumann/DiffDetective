﻿{-# LANGUAGE GADTs #-}

module MinimalLabels where

import VariationTree
import Logic
import Data.Maybe ( fromJust )

data MinimalLabels f where
    Artifact :: ArtifactReference -> MinimalLabels f
    Mapping :: (Composable f) => f -> MinimalLabels f

-- TODO: How to make this implementation a mandatory core of the VTLabel type class to avoid redundancy?
instance VTLabel MinimalLabels where
    makeArtifactLabel = Artifact
    makeMappingLabel = Mapping

    featuremapping tree node@(VTNode _ label) = case label of
        Artifact _ -> fromJust $ featureMappingOfParent tree node
        Mapping f -> f
    presencecondition tree node@(VTNode _ label) = case label of 
        Artifact _ -> parentPC
        Mapping f -> land [f, parentPC]
        where
            parentPC = fromJust $ presenceConditionOfParent tree node

instance Comparable f => Eq (MinimalLabels f) where
    x == y = case (x, y) of
        (Artifact a, Artifact b) -> a == b
        (Mapping a, Mapping b) -> lequivalent a b
        (_, _) -> False