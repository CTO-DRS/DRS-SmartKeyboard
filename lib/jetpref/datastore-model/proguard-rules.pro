# Keep generated preference model implementations: they are loaded
# reflectively via Class.forName(modelClass.qualifiedName + "Impl").
-keep class * extends org.drs.jetpref.datastore.model.PreferenceModel { *; }
