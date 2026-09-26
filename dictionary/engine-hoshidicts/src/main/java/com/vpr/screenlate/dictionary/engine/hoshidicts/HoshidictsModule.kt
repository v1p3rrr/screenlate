package com.vpr.screenlate.dictionary.engine.hoshidicts

import com.vpr.screenlate.dictionary.api.DictionaryEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HoshidictsModule {
    @Binds
    abstract fun bindEngine(engine: HoshidictsEngine): DictionaryEngine
}
