package pl.qprogramming.magicmirror.data;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class DataContainer implements Serializable {
    private Object data;
}
