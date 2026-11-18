package com.aminfathi.nava.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val day=lightColorScheme(primary=Color(0xFF205E58),onPrimary=Color.White,secondary=Color(0xFF63795B),tertiary=Color(0xFF9B6956),
    background=Color(0xFFF8F6F0),surface=Color(0xFFFFFEFA),surfaceVariant=Color(0xFFEBEEE3),onSurface=Color(0xFF213B37),onSurfaceVariant=Color(0xFF606D64),outline=Color(0xFFACB8AC))
private val night=darkColorScheme(primary=Color(0xFFAFD6C5),onPrimary=Color(0xFF153E36),secondary=Color(0xFFBDCC9F),tertiary=Color(0xFFE4B7A3),
    background=Color(0xFF112723),surface=Color(0xFF19332E),surfaceVariant=Color(0xFF2C443C),onSurface=Color(0xFFF0F0E6),onSurfaceVariant=Color(0xFFB7C4B8))
@Composable fun NavaTheme(mode:String,content:@Composable ()->Unit) {
    val dark=mode=="dark" || mode=="system" && isSystemInDarkTheme()
    MaterialTheme(colorScheme=if(dark) night else day,shapes=Shapes(small=RoundedCornerShape(14.dp),medium=RoundedCornerShape(22.dp),large=RoundedCornerShape(28.dp)),
        typography=Typography(
            headlineLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=36.sp,lineHeight=44.sp,fontWeight=FontWeight.Medium),
            headlineMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.Serif,fontSize=28.sp,lineHeight=37.sp),
            titleLarge=androidx.compose.ui.text.TextStyle(fontSize=21.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
            bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=26.sp),
            bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=23.sp),
            labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,fontWeight=FontWeight.SemiBold)
        ),content=content)
}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier=modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable fun Action(text:String,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(18.dp),contentPadding=PaddingValues(16.dp)) { Text(text) }
}
