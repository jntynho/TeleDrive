package com.infinity.drive.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*

@Composable
private fun Modifier.cardGradient(colors:CardColors,shape:Shape)=background(Brush.linearGradient(listOf(lerp(colors.containerColor,MaterialTheme.colorScheme.primaryContainer,.35f),colors.containerColor)),shape)

@Composable
fun GradientCard(modifier:Modifier=Modifier,shape:Shape=CardDefaults.shape,colors:CardColors=CardDefaults.cardColors(),elevation:CardElevation=CardDefaults.cardElevation(),border:androidx.compose.foundation.BorderStroke?=null,content:@Composable ColumnScope.()->Unit){
    Card(modifier.cardGradient(colors,shape),shape,colors.copy(containerColor=Color.Transparent),elevation,border,content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradientCard(onClick:()->Unit,modifier:Modifier=Modifier,enabled:Boolean=true,shape:Shape=CardDefaults.shape,colors:CardColors=CardDefaults.cardColors(),elevation:CardElevation=CardDefaults.cardElevation(),border:androidx.compose.foundation.BorderStroke?=null,interactionSource:androidx.compose.foundation.interaction.MutableInteractionSource?=null,content:@Composable ColumnScope.()->Unit){
    Card(onClick,modifier.cardGradient(colors,shape),enabled,shape,colors.copy(containerColor=Color.Transparent),elevation,border,interactionSource,content)
}
