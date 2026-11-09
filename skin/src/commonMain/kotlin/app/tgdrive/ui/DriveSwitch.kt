package app.tgdrive.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Wide capsule thumb with a separate 48 dp touch target. */
@Composable
fun DriveSwitch(checked:Boolean,onCheckedChange:((Boolean)->Unit)?,modifier:Modifier=Modifier) {
    val track by animateColorAsState(if(checked)Color(0xFF0085FF)else Color(0xFFA7AAAE),tween(140),label="switch-track")
    val thumb by animateColorAsState(if(checked)Color.White else Color(0xFF111111),tween(140),label="switch-thumb")
    val offset by animateDpAsState(if(checked)22.dp else 2.dp,spring(dampingRatio=.78f,stiffness=650f),label="switch-position")
    val touch=if(onCheckedChange!=null)Modifier.toggleable(checked,role=Role.Switch,onValueChange=onCheckedChange)else Modifier
    Box(modifier.then(touch).size(64.dp,48.dp),contentAlignment=Alignment.Center) {
        Box(Modifier.size(64.dp,28.dp).background(track,DriveStyle.PillShape)) {
            Box(Modifier.offset(x=offset,y=2.dp).size(40.dp,24.dp).background(thumb,DriveStyle.PillShape))
        }
    }
}
