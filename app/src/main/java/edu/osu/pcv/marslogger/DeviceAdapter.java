package edu.osu.pcv.marslogger;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.woncan.device.Device;


public class DeviceAdapter extends BaseQuickAdapter<Device, BaseViewHolder> {

    private DeviceAdapter(int layoutResId) {
        super(layoutResId);
    }

    public DeviceAdapter() {
        this(R.layout.item_device);
    }

    @Override
    protected void convert(BaseViewHolder helper, Device item) {
        helper.setText(R.id.item_bluetooth_name, item.getName());

    }


}